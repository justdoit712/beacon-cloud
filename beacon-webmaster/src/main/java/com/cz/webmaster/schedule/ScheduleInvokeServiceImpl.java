package com.cz.webmaster.schedule;

import com.cz.webmaster.entity.ScheduleJob;
import com.cz.webmaster.entity.ScheduleLog;
import com.cz.webmaster.mapper.ScheduleJobMapper;
import com.cz.webmaster.service.ScheduleLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Date;

@Service
public class ScheduleInvokeServiceImpl implements ScheduleInvokeService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleInvokeServiceImpl.class);

    private static final int LOG_ERROR_MAX_LEN = 1900;

    @Autowired
    private ScheduleJobMapper scheduleJobMapper;

    @Autowired
    private ScheduleLogService scheduleLogService;

    @Override
    public void invoke(Long jobId) {
        ScheduleJob job = scheduleJobMapper.selectById(jobId);
        if (job == null) {
            return;
        }

        long start = System.currentTimeMillis();
        ScheduleLog scheduleLog = new ScheduleLog();
        scheduleLog.setJobId(job.getJobId());
        scheduleLog.setBeanName(job.getBeanName());
        scheduleLog.setMethodName(job.getMethodName());
        scheduleLog.setParams(job.getParams());
        scheduleLog.setCreateTime(new Date());

        try {
            doInvoke(job);
            scheduleLog.setStatus(0);
        } catch (Exception e) {
            scheduleLog.setStatus(1);
            scheduleLog.setError(errorMessage(e));
            log.error("schedule job execute error, jobId={}", jobId, e);
        } finally {
            scheduleLog.setTimes(System.currentTimeMillis() - start);
            scheduleLogService.save(scheduleLog);
        }
    }

    private void doInvoke(ScheduleJob job) throws Exception {
        Object bean = SpringContextHolder.getBean(job.getBeanName());
        String methodName = job.getMethodName();
        String params = job.getParams();

        if (!StringUtils.hasText(methodName)) {
            throw new IllegalArgumentException("methodName is required");
        }

        Method method;
        if (StringUtils.hasText(params)) {
            method = ReflectionUtils.findMethod(bean.getClass(), methodName, String.class);
            if (method == null) {
                throw new NoSuchMethodException("No method '" + methodName + "(String)' on bean " + job.getBeanName());
            }
            ReflectionUtils.makeAccessible(method);
            method.invoke(bean, params);
            return;
        }

        method = ReflectionUtils.findMethod(bean.getClass(), methodName);
        if (method == null) {
            throw new NoSuchMethodException("No method '" + methodName + "()' on bean " + job.getBeanName());
        }
        ReflectionUtils.makeAccessible(method);
        method.invoke(bean);
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String detail = sw.toString();
        if (detail.length() <= LOG_ERROR_MAX_LEN) {
            return detail;
        }
        return detail.substring(0, LOG_ERROR_MAX_LEN);
    }
}
