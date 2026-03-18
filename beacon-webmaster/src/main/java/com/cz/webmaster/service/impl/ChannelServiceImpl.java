package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.Channel;
import com.cz.webmaster.mapper.ChannelMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ChannelService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * {@link ChannelService} 的默认实现。
 *
 * <p>该实现负责通道主数据的基础增删改查，并在写操作成功后通过
 * {@link CacheSyncRuntimeExecutor} 触发 {@code channel} 域的运行时缓存同步。</p>
 *
 * <p>同步触发统一放在 Service 层，避免控制层、Mapper 层直接感知缓存写删逻辑。</p>
 */
@Service
public class ChannelServiceImpl implements ChannelService {

    /**
     * 通道数据访问入口。
     */
    @Autowired
    private ChannelMapper channelMapper;

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
     * 按分页条件查询通道列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @param offset 分页起始偏移量
     * @param limit 每页大小
     * @return 通道列表
     */
    @Override
    public List<Channel> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return channelMapper.findListByPage(query, offset, limit);
    }

    /**
     * 按关键字统计通道数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的通道数量
     */
    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return channelMapper.countByKeyword(query);
    }

    /**
     * 按主键查询通道详情。
     *
     * @param id 通道 id
     * @return 通道对象；当 id 为空或未命中时返回 {@code null}
     */
    @Override
    public Channel findById(Long id) {
        if (id == null) {
            return null;
        }
        return channelMapper.findById(id);
    }

    /**
     * 查询全部有效通道。
     *
     * @return 有效通道列表
     */
    @Override
    public List<Channel> findAllActive() {
        return channelMapper.findAllActive();
    }

    /**
     * 新增通道，并在成功后触发 {@code channel} 域缓存同步。
     *
     * <p>处理流程如下：</p>
     * <p>1. 校验核心字段是否齐全；</p>
     * <p>2. 补齐 id、创建时间、更新时间和默认状态；</p>
     * <p>3. 写入数据库；</p>
     * <p>4. 查询最新记录；</p>
     * <p>5. 通过 {@link CacheSyncRuntimeExecutor} 在事务提交后执行缓存 upsert。</p>
     *
     * @param channel 通道对象
     * @return {@code true} 表示新增成功，{@code false} 表示新增失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Channel channel) {
        if (channel == null
                || !StringUtils.hasText(channel.getChannelName())
                || channel.getChannelType() == null
                || !StringUtils.hasText(channel.getChannelArea())
                || channel.getChannelPrice() == null
                || channel.getProtocolType() == null) {
            return false;
        }
        if (channel.getId() == null) {
            channel.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        channel.setCreated(now);
        channel.setUpdated(now);
        if (channel.getIsDelete() == null) {
            channel.setIsDelete((byte) 0);
        }
        if (channel.getIsAvailable() == null) {
            channel.setIsAvailable((byte) 0);
        }

        boolean saved = channelMapper.insertSelective(channel) > 0;
        if (!saved) {
            return false;
        }
        Channel latest = channelMapper.findById(channel.getId());
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CHANNEL, latest != null ? latest : channel),
                CacheDomainRegistry.CHANNEL,
                "upsert",
                safeEntityId(channel.getId())
        );
        return true;
    }

    /**
     * 更新通道，并在成功后触发 {@code channel} 域缓存同步。
     *
     * @param channel 通道对象
     * @return {@code true} 表示更新成功，{@code false} 表示更新失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(Channel channel) {
        if (channel == null || channel.getId() == null) {
            return false;
        }
        channel.setUpdated(new Date());
        boolean updated = channelMapper.updateById(channel) > 0;
        if (!updated) {
            return false;
        }
        Channel latest = channelMapper.findById(channel.getId());
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CHANNEL, latest != null ? latest : channel),
                CacheDomainRegistry.CHANNEL,
                "upsert",
                safeEntityId(channel.getId())
        );
        return true;
    }

    /**
     * 批量删除通道，并在成功后逐个触发 {@code channel} 域缓存删除同步。
     *
     * <p>数据库删除成功后，会对传入的每个有效 id 分别注册一次缓存删除操作，
     * 以保证缓存侧逐条失效。</p>
     *
     * @param ids 需要删除的通道 id 集合
     * @param updateId 本次操作人 id；允许为 {@code null}
     * @return {@code true} 表示删除成功，{@code false} 表示删除失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        boolean deleted = channelMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                continue;
            }
            cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                    () -> cacheSyncService.syncDelete(CacheDomainRegistry.CHANNEL, id),
                    CacheDomainRegistry.CHANNEL,
                    "delete",
                    safeEntityId(id)
            );
        }
        return true;
    }

    /**
     * 将通道 id 转换为适合日志输出的实体标识。
     *
     * @param id 通道 id
     * @return 非空的日志实体标识；当 id 为空时返回 {@code "-"}
     */
    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
