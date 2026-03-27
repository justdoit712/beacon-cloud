package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.MobileTransfer;
import com.cz.webmaster.mapper.MobileTransferMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.MobileTransferService;
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
public class MobileTransferServiceImpl implements MobileTransferService {

    private static final Logger log = LoggerFactory.getLogger(MobileTransferServiceImpl.class);

    private final MobileTransferMapper mobileTransferMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    public MobileTransferServiceImpl(MobileTransferMapper mobileTransferMapper,
                                     CacheSyncService cacheSyncService,
                                     CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.mobileTransferMapper = mobileTransferMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    @Override
    public List<MobileTransfer> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return mobileTransferMapper.findListByPage(query, offset, limit);
    }

    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return mobileTransferMapper.countByKeyword(query);
    }

    @Override
    public MobileTransfer findById(Long id) {
        if (id == null) {
            return null;
        }
        return mobileTransferMapper.findById(id);
    }

    @Override
    public List<MobileTransfer> findAllActive() {
        return mobileTransferMapper.findAllActive();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(MobileTransfer mobileTransfer) {
        if (!isValidForSave(mobileTransfer)) {
            return false;
        }
        if (mobileTransfer.getId() == null) {
            mobileTransfer.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        mobileTransfer.setCreated(now);
        mobileTransfer.setUpdated(now);
        if (mobileTransfer.getIsDelete() == null) {
            mobileTransfer.setIsDelete((byte) 0);
        }

        boolean saved = mobileTransferMapper.insertSelective(mobileTransfer) > 0;
        if (!saved) {
            return false;
        }
        MobileTransfer latest = mobileTransferMapper.findById(mobileTransfer.getId());
        scheduleTransferUpsert(latest != null ? latest : mobileTransfer, "upsert");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(MobileTransfer mobileTransfer) {
        if (mobileTransfer == null || mobileTransfer.getId() == null) {
            return false;
        }
        MobileTransfer before = mobileTransferMapper.findById(mobileTransfer.getId());
        mobileTransfer.setUpdated(new Date());
        boolean updated = mobileTransferMapper.updateById(mobileTransfer) > 0;
        if (!updated) {
            return false;
        }
        MobileTransfer latest = mobileTransferMapper.findById(mobileTransfer.getId());
        String beforeIdentity = transferIdentity(before);
        String afterIdentity = transferIdentity(latest);

        if (StringUtils.hasText(beforeIdentity)
                && !Objects.equals(beforeIdentity, afterIdentity)
                && before != null) {
            scheduleTransferDelete(before, "delete.oldKey");
        }
        if (latest != null) {
            scheduleTransferUpsert(latest, "upsert");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        List<MobileTransfer> beforeList = mobileTransferMapper.findByIds(ids);
        boolean deleted = mobileTransferMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        if (beforeList != null) {
            for (MobileTransfer item : beforeList) {
                scheduleTransferDelete(item, "delete");
            }
        }
        return true;
    }

    private boolean isValidForSave(MobileTransfer mobileTransfer) {
        return mobileTransfer != null
                && StringUtils.hasText(mobileTransfer.getTransferNumber())
                && StringUtils.hasText(mobileTransfer.getAreaCode())
                && mobileTransfer.getInitIsp() != null
                && mobileTransfer.getNowIsp() != null
                && mobileTransfer.getIsTransfer() != null;
    }

    private String transferIdentity(MobileTransfer entity) {
        Map<String, Object> payload = buildTransferPayload(entity);
        if (payload == null) {
            return null;
        }
        return payload.get("mobile") + "|" + payload.get("value");
    }

    private void scheduleTransferUpsert(MobileTransfer entity, String operation) {
        final Map<String, Object> payload = buildTransferPayload(entity);
        if (payload == null) {
            log.debug("runtime sync skip mobile_transfer->transfer upsert because payload is invalid, entity={}", entity);
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.TRANSFER, payload),
                CacheDomainRegistry.TRANSFER,
                operation,
                safeEntityId(entity == null ? null : entity.getId())
        );
    }

    private void scheduleTransferDelete(MobileTransfer entity, String operation) {
        final Map<String, Object> payload = buildTransferPayload(entity);
        if (payload == null) {
            log.debug("runtime sync skip mobile_transfer->transfer delete because payload is invalid, entity={}", entity);
            return;
        }
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncDelete(CacheDomainRegistry.TRANSFER, payload),
                CacheDomainRegistry.TRANSFER,
                operation,
                safeEntityId(entity == null ? null : entity.getId())
        );
    }

    private Map<String, Object> buildTransferPayload(MobileTransfer entity) {
        if (entity == null
                || !StringUtils.hasText(entity.getTransferNumber())
                || entity.getNowIsp() == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobile", entity.getTransferNumber().trim());
        payload.put("value", String.valueOf(entity.getNowIsp()));
        return payload;
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
