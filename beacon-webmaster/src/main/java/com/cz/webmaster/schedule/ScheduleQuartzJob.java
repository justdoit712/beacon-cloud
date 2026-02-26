package com.cz.webmaster.schedule;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class ScheduleQuartzJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long jobId = context.getMergedJobDataMap().getLong("jobId");
        try {
            ScheduleInvokeService invokeService = SpringContextHolder.getBean(ScheduleInvokeService.class);
            invokeService.invoke(jobId);
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }
}
