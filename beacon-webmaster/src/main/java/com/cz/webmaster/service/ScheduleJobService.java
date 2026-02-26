package com.cz.webmaster.service;

import com.cz.webmaster.entity.ScheduleJob;

import java.util.List;

public interface ScheduleJobService {

    long count(String keyword);

    List<ScheduleJob> list(String keyword, int offset, int limit);

    ScheduleJob findById(Long jobId);

    boolean save(ScheduleJob job);

    boolean update(ScheduleJob job);

    boolean deleteBatch(List<Long> jobIds);

    boolean pauseBatch(List<Long> jobIds);

    boolean resumeBatch(List<Long> jobIds);

    boolean runBatch(List<Long> jobIds);
}
