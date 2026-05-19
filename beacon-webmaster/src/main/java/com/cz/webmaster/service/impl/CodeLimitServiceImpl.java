package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.entity.CodeLimit;
import com.cz.webmaster.mapper.CodeLimitMapper;
import com.cz.webmaster.service.CodeLimitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class CodeLimitServiceImpl implements CodeLimitService {

    private final CodeLimitMapper codeLimitMapper;

    public CodeLimitServiceImpl(CodeLimitMapper codeLimitMapper) {
        this.codeLimitMapper = codeLimitMapper;
    }

    @Override
    public List<CodeLimit> findListByPage(String keyword, int offset, int limit) {
        String query = keyword == null ? null : keyword.trim();
        return codeLimitMapper.findListByPage(query, offset, limit);
    }

    @Override
    public long countByKeyword(String keyword) {
        String query = keyword == null ? null : keyword.trim();
        return codeLimitMapper.countByKeyword(query);
    }

    @Override
    public CodeLimit findById(Long id) {
        return id == null ? null : codeLimitMapper.findById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(CodeLimit codeLimit) {
        if (!isValidForSave(codeLimit)) {
            return false;
        }
        if (codeLimit.getId() == null) {
            codeLimit.setId(IdUtil.getSnowflakeNextId());
        }
        Date now = new Date();
        codeLimit.setCreated(now);
        codeLimit.setUpdated(now);
        if (codeLimit.getIsDelete() == null) {
            codeLimit.setIsDelete((byte) 0);
        }
        return codeLimitMapper.insertSelective(codeLimit) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(CodeLimit codeLimit) {
        if (codeLimit == null || codeLimit.getId() == null) {
            return false;
        }
        codeLimit.setUpdated(new Date());
        return codeLimitMapper.updateById(codeLimit) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> ids, Long updateId) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return codeLimitMapper.deleteBatch(ids, new Date(), updateId) > 0;
    }

    private boolean isValidForSave(CodeLimit codeLimit) {
        return codeLimit != null
                && codeLimit.getLimitTime() != null
                && codeLimit.getLimitCount() != null
                && StringUtils.hasText(codeLimit.getDescription());
    }
}
