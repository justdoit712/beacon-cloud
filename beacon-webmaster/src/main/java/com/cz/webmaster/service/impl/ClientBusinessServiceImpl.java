package com.cz.webmaster.service.impl;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientBusinessExample;
import com.cz.webmaster.mapper.ClientBusinessMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * {@link ClientBusinessService} 的默认实现。
 *
 * <p>该实现负责客户业务主数据的基础增删改查，并在写操作成功后通过
 * {@link CacheSyncRuntimeExecutor} 触发 {@code client_business} 域的运行时缓存同步。</p>
 *
 * <p>由于 {@code client_business} 的缓存 key 依赖 {@code apiKey}，
 * 更新时如果 {@code apiKey} 发生变化，还需要额外删除旧 key，避免缓存残留。</p>
 */
@Service
public class ClientBusinessServiceImpl implements ClientBusinessService {

    /**
     * 客户业务数据访问入口。
     */
    @Autowired
    private ClientBusinessMapper clientBusinessMapper;

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
     * 查询全部客户业务信息。
     *
     * @return 客户业务列表
     */
    @Override
    public List<ClientBusiness> findAll() {
        return clientBusinessMapper.selectByExample(null);
    }

    /**
     * 根据用户 id 查询其可见的客户业务信息。
     *
     * @param userId 用户 id
     * @return 客户业务列表
     */
    @Override
    public List<ClientBusiness> findByUserId(Integer userId) {
        ClientBusinessExample example = new ClientBusinessExample();
        example.createCriteria().andExtend1EqualTo(userId + "");
        return clientBusinessMapper.selectByExample(example);
    }

    /**
     * 根据关键字查询客户业务列表。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 客户业务列表
     */
    @Override
    public List<ClientBusiness> findByKeyword(String keyword) {
        ClientBusinessExample example = buildExample(keyword);
        example.setOrderByClause("id desc");
        return clientBusinessMapper.selectByExample(example);
    }

    /**
     * 根据关键字统计客户业务数量。
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 匹配的客户业务数量
     */
    @Override
    public long countByKeyword(String keyword) {
        ClientBusinessExample example = buildExample(keyword);
        return clientBusinessMapper.countByExample(example);
    }

    /**
     * 根据主键查询客户业务详情。
     *
     * @param id 客户业务 id
     * @return 客户业务对象；未命中时返回 {@code null}
     */
    @Override
    public ClientBusiness findById(Long id) {
        return clientBusinessMapper.selectByPrimaryKey(id);
    }

    /**
     * 新增客户业务信息，并在成功后触发 {@code client_business} 域缓存同步。
     *
     * <p>处理流程如下：</p>
     * <p>1. 校验对象是否为空；</p>
     * <p>2. 补齐 id、默认过滤器、创建时间、更新时间和默认删除状态；</p>
     * <p>3. 写入数据库；</p>
     * <p>4. 查询最新记录；</p>
     * <p>5. 通过 {@link CacheSyncRuntimeExecutor} 在事务提交后执行缓存 upsert。</p>
     *
     * @param clientBusiness 客户业务对象
     * @return {@code true} 表示新增成功，{@code false} 表示新增失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ClientBusiness clientBusiness) {
        if (clientBusiness == null) {
            return false;
        }
        if (clientBusiness.getId() == null) {
            clientBusiness.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextId());
        }
        if (!StringUtils.hasText(clientBusiness.getClientFilters())) {
            clientBusiness.setClientFilters("blackGlobal,blackClient,dirtyword,route");
        }

        Date now = new Date();
        clientBusiness.setCreated(now);
        clientBusiness.setUpdated(now);
        if (clientBusiness.getIsDelete() == null) {
            clientBusiness.setIsDelete((byte) 0);
        }

        boolean saved = clientBusinessMapper.insertSelective(clientBusiness) > 0;
        if (!saved) {
            return false;
        }

        ClientBusiness latest = clientBusinessMapper.selectByPrimaryKey(clientBusiness.getId());
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest != null ? latest : clientBusiness),
                CacheDomainRegistry.CLIENT_BUSINESS,
                "upsert",
                safeEntityId(clientBusiness.getId())
        );
        return true;
    }

    /**
     * 更新客户业务信息，并在成功后触发 {@code client_business} 域缓存同步。
     *
     * <p>当更新前后的 {@code apiKey} 不一致时，说明缓存 key 已发生变化。
     * 此时需要先删除旧 key，再写入新 key，以避免 Redis 中保留失效的旧数据。</p>
     *
     * @param clientBusiness 客户业务对象
     * @return {@code true} 表示更新成功，{@code false} 表示更新失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ClientBusiness clientBusiness) {
        if (clientBusiness == null || clientBusiness.getId() == null) {
            return false;
        }
        if (clientBusiness.getExtend4() != null) {
            return false;
        }

        ClientBusiness before = clientBusinessMapper.selectByPrimaryKey(clientBusiness.getId());
        clientBusiness.setUpdated(new Date());
        boolean updated = clientBusinessMapper.updateByPrimaryKeySelective(clientBusiness) > 0;
        if (!updated) {
            return false;
        }

        ClientBusiness latest = clientBusinessMapper.selectByPrimaryKey(clientBusiness.getId());
        if (before != null
                && latest != null
                && StringUtils.hasText(before.getApikey())
                && StringUtils.hasText(latest.getApikey())
                && !Objects.equals(before.getApikey(), latest.getApikey())) {
            cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                    () -> cacheSyncService.syncDelete(CacheDomainRegistry.CLIENT_BUSINESS, before),
                    CacheDomainRegistry.CLIENT_BUSINESS,
                    "delete.oldKey",
                    safeEntityId(before.getId())
            );
        }

        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.CLIENT_BUSINESS, latest != null ? latest : clientBusiness),
                CacheDomainRegistry.CLIENT_BUSINESS,
                "upsert",
                safeEntityId(clientBusiness.getId())
        );
        return true;
    }

    /**
     * 批量逻辑删除客户业务信息，并在成功后触发 {@code client_business} 域缓存删除同步。
     *
     * <p>该方法采用逐条逻辑删除的方式处理，每删除一条有效记录，
     * 就注册一次对应的缓存删除动作。</p>
     *
     * @param ids 需要删除的客户业务 id 集合
     * @return {@code true} 表示删除成功，{@code false} 表示删除失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }

        Date now = new Date();
        for (Long id : ids) {
            ClientBusiness existing = clientBusinessMapper.selectByPrimaryKey(id);
            ClientBusiness cb = new ClientBusiness();
            cb.setId(id);
            cb.setIsDelete((byte) 1);
            cb.setUpdated(now);
            if (clientBusinessMapper.updateByPrimaryKeySelective(cb) <= 0) {
                return false;
            }
            if (existing != null && StringUtils.hasText(existing.getApikey())) {
                cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                        () -> cacheSyncService.syncDelete(CacheDomainRegistry.CLIENT_BUSINESS, existing),
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        "delete",
                        safeEntityId(existing.getId())
                );
            }
        }
        return true;
    }

    /**
     * 构造客户业务查询条件。
     *
     * <p>当前仅查询未删除数据，并在传入关键字时按公司名称模糊匹配。</p>
     *
     * @param keyword 查询关键字；允许为 {@code null}
     * @return 查询条件对象
     */
    private ClientBusinessExample buildExample(String keyword) {
        ClientBusinessExample example = new ClientBusinessExample();
        ClientBusinessExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        if (StringUtils.hasText(keyword)) {
            criteria.andCorpnameLike("%" + keyword.trim() + "%");
        }
        return example;
    }

    /**
     * 将客户业务 id 转换为适合日志输出的实体标识。
     *
     * @param id 客户业务 id
     * @return 非空的日志实体标识；当 id 为空时返回 {@code "-"}
     */
    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
