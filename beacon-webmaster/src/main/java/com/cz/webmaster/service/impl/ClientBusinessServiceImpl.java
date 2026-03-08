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

@Service
public class ClientBusinessServiceImpl implements ClientBusinessService {

    @Autowired
    private ClientBusinessMapper clientBusinessMapper;
    @Autowired
    private CacheSyncService cacheSyncService;
    @Autowired
    private CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    @Override
    public List<ClientBusiness> findAll() {
        return clientBusinessMapper.selectByExample(null);
    }

    @Override
    public List<ClientBusiness> findByUserId(Integer userId) {
        ClientBusinessExample example = new ClientBusinessExample();
        example.createCriteria().andExtend1EqualTo(userId + "");
        return clientBusinessMapper.selectByExample(example);
    }

    @Override
    public List<ClientBusiness> findByKeyword(String keyword) {
        ClientBusinessExample example = buildExample(keyword);
        example.setOrderByClause("id desc");
        return clientBusinessMapper.selectByExample(example);
    }

    @Override
    public long countByKeyword(String keyword) {
        ClientBusinessExample example = buildExample(keyword);
        return clientBusinessMapper.countByExample(example);
    }

    @Override
    public ClientBusiness findById(Long id) {
        return clientBusinessMapper.selectByPrimaryKey(id);
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ClientBusiness clientBusiness) {
        if (clientBusiness == null || clientBusiness.getId() == null) {
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

    private ClientBusinessExample buildExample(String keyword) {
        ClientBusinessExample example = new ClientBusinessExample();
        ClientBusinessExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        if (StringUtils.hasText(keyword)) {
            criteria.andCorpnameLike("%" + keyword.trim() + "%");
        }
        return example;
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}

