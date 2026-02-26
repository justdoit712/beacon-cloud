package com.cz.webmaster.schedule;

import com.cz.webmaster.entity.ScheduleJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

public final class QuartzUtils {

    private static final String JOB_PREFIX = "SCHEDULE_JOB_";
    private static final String TRIGGER_PREFIX = "SCHEDULE_TRIGGER_";

    private QuartzUtils() {
    }

    public static TriggerKey getTriggerKey(Long jobId) {
        return TriggerKey.triggerKey(TRIGGER_PREFIX + jobId);
    }

    public static JobKey getJobKey(Long jobId) {
        return JobKey.jobKey(JOB_PREFIX + jobId);
    }

    public static void createOrUpdateSchedule(Scheduler scheduler, ScheduleJob job) throws SchedulerException {
        JobKey jobKey = getJobKey(job.getJobId());
        TriggerKey triggerKey = getTriggerKey(job.getJobId());
        CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(job.getCronExpression());

        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        if (trigger == null) {
            JobDetail jobDetail = JobBuilder.newJob(ScheduleQuartzJob.class)
                    .withIdentity(jobKey)
                    .build();
            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            jobDataMap.put("jobId", job.getJobId());
            trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(scheduleBuilder)
                    .build();
            scheduler.scheduleJob(jobDetail, trigger);
        } else {
            trigger = trigger.getTriggerBuilder()
                    .withIdentity(triggerKey)
                    .withSchedule(scheduleBuilder)
                    .build();
            scheduler.rescheduleJob(triggerKey, trigger);
        }

        if (job.getStatus() != null && job.getStatus() == ScheduleStatus.PAUSE) {
            pauseJob(scheduler, job.getJobId());
        } else {
            resumeJob(scheduler, job.getJobId());
        }
    }

    public static void deleteSchedule(Scheduler scheduler, Long jobId) throws SchedulerException {
        scheduler.deleteJob(getJobKey(jobId));
    }

    public static void runOnce(Scheduler scheduler, Long jobId) throws SchedulerException {
        scheduler.triggerJob(getJobKey(jobId));
    }

    public static void pauseJob(Scheduler scheduler, Long jobId) throws SchedulerException {
        scheduler.pauseJob(getJobKey(jobId));
    }

    public static void resumeJob(Scheduler scheduler, Long jobId) throws SchedulerException {
        scheduler.resumeJob(getJobKey(jobId));
    }
}
