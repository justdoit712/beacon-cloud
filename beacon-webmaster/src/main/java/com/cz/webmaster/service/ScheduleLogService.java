package com.cz.webmaster.service;

import com.cz.webmaster.entity.ScheduleLog;

import java.util.List;

public interface ScheduleLogService {

    long count(String keyword);

    List<ScheduleLog> list(String keyword, int offset, int limit);

    boolean save(ScheduleLog log);

    boolean deleteBatch(List<Long> logIds);
}
