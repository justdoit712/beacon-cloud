package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.MobileBlack;
import com.cz.webmaster.mapper.MobileBlackMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.MobileBlackService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MobileBlackServiceImpl implements MobileBlackService {

    private static final Logger log = LoggerFactory.getLogger(MobileBlackServiceImpl.class);

    private final MobileBlackMapper mobileBlackMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    public MobileBlackServiceImpl(MobileBlackMapper mobileBlackMapper,
                                  CacheSyncService cacheSyncService,
                                  CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.mobileBlackMapper = mobileBlackMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    @Override
    public List<MobileBlack> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return mobileBlackMapper.findListByPage(query, offset, limit);
    }

    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return mobileBlackMapper.countByKeyword(query);
    }

    @Override
    public MobileBlack findById(Long id) {
        return id == null ? null : mobileBlackMapper.findById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(MobileBlack mobileBlack) {
        if (!isValidForSave(mobileBlack)) {
            return false;
        }
        if (mobileBlack.getId() == null) {
            mobileBlack.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        mobileBlack.setCreated(now);
        mobileBlack.setUpdated(now);
        if (mobileBlack.getBlackType() == null) {
            mobileBlack.setBlackType(0);
        }
        if (mobileBlack.getClientId() == null) {
            mobileBlack.setClientId(0);
        }
        if (mobileBlack.getIsDelete() == null) {
            mobileBlack.setIsDelete((byte) 0);
        }

        boolean saved = mobileBlackMapper.insertSelective(mobileBlack) > 0;
        if (!saved) {
            return false;
        }
        MobileBlack latest = mobileBlackMapper.findById(mobileBlack.getId());
        scheduleBlackUpsert(latest != null ? latest : mobileBlack, "upsert");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(MobileBlack mobileBlack) {
        if (mobileBlack == null || mobileBlack.getId() == null) {
            return false;
        }
        MobileBlack before = mobileBlackMapper.findById(mobileBlack.getId());
        if (before == null) {
            return false;
        }
        mobileBlack.setUpdated(new Date());
        boolean updated = mobileBlackMapper.updateById(mobileBlack) > 0;
        if (!updated) {
            return false;
        }

        MobileBlack latest = mobileBlackMapper.findById(mobileBlack.getId());
        String beforeIdentity = blackIdentity(before);
        String afterIdentity = blackIdentity(latest);
        if (StringUtils.hasText(beforeIdentity)
                && !Objects.equals(beforeIdentity, afterIdentity)) {
            scheduleBlackDelete(before, "delete.oldKey");
        }
        if (latest != null) {
            scheduleBlackUpsert(latest, "upsert");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        List<MobileBlack> beforeList = mobileBlackMapper.findByIds(ids);
        boolean deleted = mobileBlackMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        if (beforeList != null) {
            for (MobileBlack item : beforeList) {
                scheduleBlackDelete(item, "delete");
            }
        }
        return true;
    }

    private boolean isValidForSave(MobileBlack mobileBlack) {
        return mobileBlack != null && StringUtils.hasText(mobileBlack.getBlackNumber());
    }

    private String blackIdentity(MobileBlack entity) {
        Map<String, Object> payload = buildBlackPayload(entity);
        if (payload == null) {
            return null;
        }
        Object clientId = payload.get("clientId");
        String mobile = String.valueOf(payload.get("mobile"));
        return clientId == null ? mobile : clientId + ":" + mobile;
    }

    private void scheduleBlackUpsert(MobileBlack entity, String operation) {
        final Map<String, Object> payload = buildBlackPayload(entity);
        if (payload == null) {
            log.debug("runtime sync skip black upsert because payload is invalid, entity={}", entity);
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.BLACK, payload),
                CacheDomainRegistry.BLACK,
                operation,
                safeEntityId(entity == null ? null : entity.getId())
        );
    }

    private void scheduleBlackDelete(MobileBlack entity, String operation) {
        final Map<String, Object> payload = buildBlackPayload(entity);
        if (payload == null) {
            log.debug("runtime sync skip black delete because payload is invalid, entity={}", entity);
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncDelete(CacheDomainRegistry.BLACK, payload),
                CacheDomainRegistry.BLACK,
                operation,
                safeEntityId(entity == null ? null : entity.getId())
        );
    }

    private Map<String, Object> buildBlackPayload(MobileBlack entity) {
        if (entity == null || !StringUtils.hasText(entity.getBlackNumber())) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobile", entity.getBlackNumber().trim());
        if (entity.getClientId() != null && entity.getClientId() > 0) {
            payload.put("clientId", entity.getClientId());
        }
        return payload;
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
