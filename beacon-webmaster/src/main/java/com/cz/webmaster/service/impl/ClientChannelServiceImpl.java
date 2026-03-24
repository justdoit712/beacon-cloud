package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.ClientChannel;
import com.cz.webmaster.mapper.ClientChannelMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ClientChannelService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ClientChannelService} 的默认实现。
 *
 * <p>该实现负责客户与通道绑定关系的基础增删改查，并在写操作成功后通过
 * {@link CacheSyncRuntimeExecutor} 触发 {@code client_channel} 域的运行时缓存同步。</p>
 *
 * <p>{@code client_channel} 域是按 {@code clientId} 聚合的集合型缓存，
 * 因此同步时不能只同步单条绑定关系，而是需要按受影响的 {@code clientId}
 * 查询全量快照后整组重建。</p>
 */
@Service
public class ClientChannelServiceImpl implements ClientChannelService {

    /**
     * 客户通道数据访问入口。
     */
    @Autowired
    private ClientChannelMapper clientChannelMapper;

    /**
     * 缓存同步统一门面。
     */
    @Autowired
    private CacheSyncService cacheSyncService;

    /**
     * 运行时缓存同步执行器。
     *
     * <p>有事务时在事务提交后执行同步，无事务时立即执行。</p>
     */
    @Autowired
    private CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    /**
     * 按分页条件查询客户通道列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @param offset 分页起始偏移量
     * @param limit 每页大小
     * @return 客户通道列表
     */
    @Override
    public List<ClientChannel> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return clientChannelMapper.findListByPage(query, offset, limit);
    }

    /**
     * 按关键字统计客户通道数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的客户通道数量
     */
    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return clientChannelMapper.countByKeyword(query);
    }

    /**
     * 按主键查询客户通道详情。
     *
     * @param id 客户通道 id
     * @return 客户通道对象；当 id 为空或未命中时返回 {@code null}
     */
    @Override
    public ClientChannel findById(Long id) {
        if (id == null) {
            return null;
        }
        return clientChannelMapper.findById(id);
    }

    /**
     * 新增客户通道绑定关系，并在成功后重建对应客户的通道集合缓存。
     *
     * <p>由于 {@code client_channel} 是按 {@code clientId} 聚合的集合型缓存，
     * 新增成功后不会只同步当前这条绑定关系，而是会按该 {@code clientId}
     * 查出全量成员快照后整组重建。</p>
     *
     * @param clientChannel 客户通道对象
     * @return {@code true} 表示新增成功，{@code false} 表示新增失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ClientChannel clientChannel) {
        if (clientChannel == null
                || clientChannel.getClientId() == null
                || clientChannel.getChannelId() == null
                || !StringUtils.hasText(clientChannel.getExtendNumber())
                || clientChannel.getPrice() == null) {
            return false;
        }
        if (clientChannel.getId() == null) {
            clientChannel.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        clientChannel.setCreated(now);
        clientChannel.setUpdated(now);
        if (clientChannel.getIsDelete() == null) {
            clientChannel.setIsDelete((byte) 0);
        }
        boolean saved = clientChannelMapper.insertSelective(clientChannel) > 0;
        if (!saved) {
            return false;
        }
        scheduleClientChannelRebuild(clientChannel.getClientId(), "upsert", safeEntityId(clientChannel.getId()));
        return true;
    }

    /**
     * 更新客户通道绑定关系，并重建受影响客户的通道集合缓存。
     *
     * <p>更新前后如果 {@code clientId} 发生变化，则旧客户和新客户两边的集合都可能受影响，
     * 因此会分别对所有受影响的 {@code clientId} 触发一次全量快照重建。</p>
     *
     * @param clientChannel 客户通道对象
     * @return {@code true} 表示更新成功，{@code false} 表示更新失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ClientChannel clientChannel) {
        if (clientChannel == null || clientChannel.getId() == null) {
            return false;
        }

        ClientChannel before = clientChannelMapper.findById(clientChannel.getId());
        clientChannel.setUpdated(new Date());
        boolean updated = clientChannelMapper.updateById(clientChannel) > 0;
        if (!updated) {
            return false;
        }

        ClientChannel latest = clientChannelMapper.findById(clientChannel.getId());
        Set<Long> affectedClientIds = new LinkedHashSet<>();
        addClientId(affectedClientIds, before == null ? null : before.getClientId());
        addClientId(affectedClientIds, latest == null ? null : latest.getClientId());
        for (Long clientId : affectedClientIds) {
            scheduleClientChannelRebuild(clientId, "upsert", safeEntityId(clientChannel.getId()));
        }
        return true;
    }

    /**
     * 批量逻辑删除客户通道绑定关系，并重建受影响客户的通道集合缓存。
     *
     * <p>删除前会先收集所有受影响的 {@code clientId}，删除成功后再按这些客户 id
     * 分别触发一次全量快照重建。</p>
     *
     * @param ids 需要删除的客户通道 id 集合
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return {@code true} 表示删除成功，{@code false} 表示删除失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }

        List<ClientChannel> beforeList = clientChannelMapper.findByIds(ids);
        Set<Long> affectedClientIds = new LinkedHashSet<>();
        if (beforeList != null) {
            for (ClientChannel item : beforeList) {
                addClientId(affectedClientIds, item == null ? null : item.getClientId());
            }
        }

        boolean deleted = clientChannelMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        for (Long clientId : affectedClientIds) {
            scheduleClientChannelRebuild(clientId, "delete", safeEntityId(clientId));
        }
        return true;
    }

    /**
     * 为指定客户注册一次客户通道集合重建任务。
     *
     * <p>真正执行时，会重新查询该客户当前全部有效通道成员，
     * 然后通过 {@code client_channel} 域的 upsert 路由完成整组集合重建。</p>
     *
     * @param clientId 客户 id
     * @param operation 本次同步操作名称，例如 {@code upsert}、{@code delete}
     * @param entityId 用于日志输出的实体标识
     */
    private void scheduleClientChannelRebuild(Long clientId, String operation, String entityId) {
        if (clientId == null || clientId <= 0) {
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_CHANNEL, buildClientChannelPayload(clientId)),
                CacheDomainRegistry.CLIENT_CHANNEL,
                operation,
                entityId
        );
    }

    /**
     * 构造客户通道集合缓存所需的全量快照 payload。
     *
     * <p>返回结果中固定包含：</p>
     * <p>1. {@code clientId}：当前客户 id；</p>
     * <p>2. {@code members}：当前客户全部有效路由成员列表。</p>
     *
     * @param clientId 客户 id
     * @return 用于 {@code client_channel} 域同步的 payload
     */
    private Map<String, Object> buildClientChannelPayload(Long clientId) {
        List<Map<String, Object>> members = clientChannelMapper.findRouteMembersByClientIds(Collections.singletonList(clientId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", clientId);
        payload.put("members", members == null ? new ArrayList<>() : members);
        return payload;
    }

    /**
     * 向受影响客户 id 集合中加入一个有效客户 id。
     *
     * @param clientIds 客户 id 集合
     * @param clientId 待加入的客户 id
     */
    private void addClientId(Set<Long> clientIds, Long clientId) {
        if (clientId != null && clientId > 0) {
            clientIds.add(clientId);
        }
    }

    /**
     * 将客户通道 id 转换为适合日志输出的实体标识。
     *
     * @param id 客户通道 id
     * @return 非空的日志实体标识；当 id 为空时返回 {@code "-"}
     */
    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
