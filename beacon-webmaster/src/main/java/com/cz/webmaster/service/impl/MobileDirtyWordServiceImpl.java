package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.entity.MobileDirtyWord;
import com.cz.webmaster.mapper.MobileDirtyWordMapper;
import com.cz.webmaster.service.CacheSyncService;
import com.cz.webmaster.service.MobileDirtyWordService;
import com.cz.webmaster.support.CacheSyncRuntimeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class MobileDirtyWordServiceImpl implements MobileDirtyWordService {

    private static final Logger log = LoggerFactory.getLogger(MobileDirtyWordServiceImpl.class);

    private final MobileDirtyWordMapper mobileDirtyWordMapper;
    private final CacheSyncService cacheSyncService;
    private final CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor;

    public MobileDirtyWordServiceImpl(MobileDirtyWordMapper mobileDirtyWordMapper,
                                      CacheSyncService cacheSyncService,
                                      CacheSyncRuntimeExecutor cacheSyncRuntimeExecutor) {
        this.mobileDirtyWordMapper = mobileDirtyWordMapper;
        this.cacheSyncService = cacheSyncService;
        this.cacheSyncRuntimeExecutor = cacheSyncRuntimeExecutor;
    }

    @Override
    public List<MobileDirtyWord> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return mobileDirtyWordMapper.findListByPage(query, offset, limit);
    }

    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return mobileDirtyWordMapper.countByKeyword(query);
    }

    @Override
    public MobileDirtyWord findById(Long id) {
        return id == null ? null : mobileDirtyWordMapper.findById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(MobileDirtyWord mobileDirtyWord) {
        if (!isValidForSave(mobileDirtyWord)) {
            return false;
        }
        if (mobileDirtyWord.getId() == null) {
            mobileDirtyWord.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        mobileDirtyWord.setCreated(now);
        mobileDirtyWord.setUpdated(now);
        if (mobileDirtyWord.getIsDelete() == null) {
            mobileDirtyWord.setIsDelete((byte) 0);
        }

        boolean saved = mobileDirtyWordMapper.insertSelective(mobileDirtyWord) > 0;
        if (!saved) {
            return false;
        }
        scheduleDirtyWordRebuild("upsert", safeEntityId(mobileDirtyWord.getId()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(MobileDirtyWord mobileDirtyWord) {
        if (mobileDirtyWord == null || mobileDirtyWord.getId() == null) {
            return false;
        }
        MobileDirtyWord before = mobileDirtyWordMapper.findById(mobileDirtyWord.getId());
        if (before == null) {
            return false;
        }
        mobileDirtyWord.setUpdated(new Date());
        boolean updated = mobileDirtyWordMapper.updateById(mobileDirtyWord) > 0;
        if (!updated) {
            return false;
        }
        scheduleDirtyWordRebuild("upsert", safeEntityId(mobileDirtyWord.getId()));
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        boolean deleted = mobileDirtyWordMapper.deleteBatch(ids, new Date(), updateId) > 0;
        if (!deleted) {
            return false;
        }
        scheduleDirtyWordRebuild("delete", "-");
        return true;
    }

    private boolean isValidForSave(MobileDirtyWord mobileDirtyWord) {
        return mobileDirtyWord != null && StringUtils.hasText(mobileDirtyWord.getDirtyword());
    }

    private void scheduleDirtyWordRebuild(String operation, String entityId) {
        cacheSyncRuntimeExecutor.runAfterCommitOrNow(
                () -> cacheSyncService.syncUpsert(CacheDomainRegistry.DIRTY_WORD, buildDirtyWordPayload()),
                CacheDomainRegistry.DIRTY_WORD,
                operation,
                entityId
        );
    }

    private Map<String, Object> buildDirtyWordPayload() {
        List<MobileDirtyWord> rows = mobileDirtyWordMapper.findAllActive();
        LinkedHashSet<String> members = new LinkedHashSet<>();
        if (rows != null) {
            for (MobileDirtyWord row : rows) {
                if (row == null || !StringUtils.hasText(row.getDirtyword())) {
                    continue;
                }
                members.add(row.getDirtyword().trim());
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("members", new ArrayList<>(members));
        if (members.isEmpty()) {
            log.debug("runtime sync dirty_word rebuild with empty members");
        }
        return payload;
    }

    private String safeEntityId(Long id) {
        return id == null ? "-" : String.valueOf(id);
    }
}
