package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.entity.ScheduleLog;
import com.cz.webmaster.mapper.ScheduleLogMapper;
import com.cz.webmaster.service.ScheduleLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class ScheduleLogServiceImpl implements ScheduleLogService {

    @Autowired
    private ScheduleLogMapper scheduleLogMapper;

    @Override
    public long count(String keyword) {
        return scheduleLogMapper.count(normalize(keyword));
    }

    @Override
    public List<ScheduleLog> list(String keyword, int offset, int limit) {
        return scheduleLogMapper.list(normalize(keyword), Math.max(offset, 0), Math.max(limit, 0));
    }

    @Override
    public boolean save(ScheduleLog log) {
        if (log == null) {
            return false;
        }
        if (log.getLogId() == null) {
            log.setLogId(IdUtil.getSnowflakeNextId());
        }
        if (log.getCreateTime() == null) {
            log.setCreateTime(new Date());
        }
        return scheduleLogMapper.insert(log) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return false;
        }
        return scheduleLogMapper.deleteBatch(logIds) > 0;
    }

    private String normalize(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }
}
