package com.cz.webmaster.service.impl;

import cn.hutool.core.util.IdUtil;
import com.cz.webmaster.entity.ScheduleJob;
import com.cz.webmaster.mapper.ScheduleJobMapper;
import com.cz.webmaster.schedule.QuartzUtils;
import com.cz.webmaster.schedule.ScheduleStatus;
import com.cz.webmaster.service.ScheduleJobService;
import org.quartz.CronExpression;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

@Service
public class ScheduleJobServiceImpl implements ScheduleJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobServiceImpl.class);

    @Autowired
    private ScheduleJobMapper scheduleJobMapper;

    @Autowired
    private Scheduler scheduler;

    @PostConstruct
    public void initScheduler() {
        List<ScheduleJob> jobs = scheduleJobMapper.selectAll();
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        for (ScheduleJob job : jobs) {
            try {
                QuartzUtils.createOrUpdateSchedule(scheduler, job);
            } catch (Exception e) {
                log.error("init quartz schedule failed, jobId={}", job.getJobId(), e);
            }
        }
    }

    @Override
    public long count(String keyword) {
        return scheduleJobMapper.count(normalize(keyword));
    }

    @Override
    public List<ScheduleJob> list(String keyword, int offset, int limit) {
        return scheduleJobMapper.list(normalize(keyword), Math.max(offset, 0), Math.max(limit, 0));
    }

    @Override
    public ScheduleJob findById(Long jobId) {
        return scheduleJobMapper.selectById(jobId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ScheduleJob job) {
        validate(job, false);
        Date now = new Date();
        if (job.getJobId() == null) {
            job.setJobId(IdUtil.getSnowflakeNextId());
        }
        if (job.getStatus() == null) {
            job.setStatus(ScheduleStatus.NORMAL);
        }
        job.setCreateTime(now);
        job.setUpdateTime(now);

        int rows = scheduleJobMapper.insert(job);
        if (rows <= 0) {
            return false;
        }
        createOrUpdateQuartz(job);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(ScheduleJob job) {
        validate(job, true);
        ScheduleJob dbJob = scheduleJobMapper.selectById(job.getJobId());
        if (dbJob == null) {
            return false;
        }
        if (job.getStatus() == null) {
            job.setStatus(dbJob.getStatus() == null ? ScheduleStatus.NORMAL : dbJob.getStatus());
        }
        job.setUpdateTime(new Date());

        int rows = scheduleJobMapper.updateById(job);
        if (rows <= 0) {
            return false;
        }
        createOrUpdateQuartz(job);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return false;
        }
        for (Long jobId : jobIds) {
            if (jobId == null) {
                continue;
            }
            try {
                QuartzUtils.deleteSchedule(scheduler, jobId);
            } catch (Exception e) {
                throw new RuntimeException("delete quartz job failed: " + jobId, e);
            }
        }
        return scheduleJobMapper.deleteBatch(jobIds) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pauseBatch(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return false;
        }
        Date now = new Date();
        int updated = scheduleJobMapper.updateStatusBatch(jobIds, ScheduleStatus.PAUSE, now);
        if (updated <= 0) {
            return false;
        }
        for (Long jobId : jobIds) {
            try {
                ensureQuartzExists(jobId);
                QuartzUtils.pauseJob(scheduler, jobId);
            } catch (Exception e) {
                throw new RuntimeException("pause quartz job failed: " + jobId, e);
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resumeBatch(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return false;
        }
        Date now = new Date();
        int updated = scheduleJobMapper.updateStatusBatch(jobIds, ScheduleStatus.NORMAL, now);
        if (updated <= 0) {
            return false;
        }
        for (Long jobId : jobIds) {
            try {
                ensureQuartzExists(jobId);
                QuartzUtils.resumeJob(scheduler, jobId);
            } catch (Exception e) {
                throw new RuntimeException("resume quartz job failed: " + jobId, e);
            }
        }
        return true;
    }

    @Override
    public boolean runBatch(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return false;
        }
        for (Long jobId : jobIds) {
            if (jobId == null) {
                continue;
            }
            try {
                ensureQuartzExists(jobId);
                QuartzUtils.runOnce(scheduler, jobId);
            } catch (Exception e) {
                throw new RuntimeException("run quartz job failed: " + jobId, e);
            }
        }
        return true;
    }

    private void ensureQuartzExists(Long jobId) throws Exception {
        if (jobId == null) {
            return;
        }
        if (scheduler.checkExists(QuartzUtils.getJobKey(jobId))) {
            return;
        }
        ScheduleJob job = scheduleJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }
        QuartzUtils.createOrUpdateSchedule(scheduler, job);
    }

    private void createOrUpdateQuartz(ScheduleJob job) {
        try {
            QuartzUtils.createOrUpdateSchedule(scheduler, job);
        } catch (Exception e) {
            throw new RuntimeException("schedule quartz job failed: " + job.getJobId(), e);
        }
    }

    private void validate(ScheduleJob job, boolean needId) {
        if (job == null) {
            throw new IllegalArgumentException("job is required");
        }
        if (needId && job.getJobId() == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (!StringUtils.hasText(job.getBeanName())) {
            throw new IllegalArgumentException("beanName is required");
        }
        if (!StringUtils.hasText(job.getMethodName())) {
            throw new IllegalArgumentException("methodName is required");
        }
        if (!StringUtils.hasText(job.getCronExpression())) {
            throw new IllegalArgumentException("cronExpression is required");
        }
        if (!CronExpression.isValidExpression(job.getCronExpression())) {
            throw new IllegalArgumentException("invalid cronExpression");
        }
    }

    private String normalize(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }
}
