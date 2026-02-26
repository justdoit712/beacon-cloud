package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheConstant;
import com.cz.common.constant.SmsConstant;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Captcha throttling in one filter: minute + hour + day.
 */
@Service("limitOneHour")
@Slf4j
public class LimitOneHourStrategyFilter implements StrategyFilter {

    private static final String UTC = "+8";

    private static final long ONE_MINUTE = 60 * 1000L - 1;

    private static final long ONE_HOUR = 60 * 60 * 1000L - 1;

    private static final long ONE_DAY = 24 * 60 * 60 * 1000L - 1;

    private static final int RETRY_COUNT = 2;

    private static final int LIMIT_MINUTE = 1;

    private static final int LIMIT_HOUR = 3;

    private static final int LIMIT_DAY = 10;

    @Autowired
    private BeaconCacheClient cacheClient;

    @Autowired
    private ErrorSendMsgUtil sendMsgUtil;

    @Override
    public void strategy(StandardSubmit submit) {
        if (submit.getState() != SmsConstant.CODE_TYPE) {
            return;
        }

        LocalDateTime sendTime = submit.getSendTime();
        long sendTimeMilli = sendTime.toInstant(ZoneOffset.of(UTC)).toEpochMilli();

        Long clientId = submit.getClientId();
        String mobile = submit.getMobile();

        minuteLimit(submit, clientId, mobile, sendTimeMilli);
        hourLimit(submit, clientId, mobile, sendTimeMilli);
        dayLimit(submit, clientId, mobile, sendTimeMilli);
    }

    private void minuteLimit(StandardSubmit submit, Long clientId, String mobile, long sendTimeMilli) {
        String key = CacheConstant.LIMIT_MINUTES + clientId + CacheConstant.SEPARATE + mobile;
        if (!Boolean.TRUE.equals(cacheClient.zadd(key, sendTimeMilli, sendTimeMilli))) {
            reject(submit, mobile, ExceptionEnums.ONE_MINUTE_LIMIT, "【策略模块-一分钟限流策略】 插入失败，满足一分钟限流规则，无法发送！");
        }

        long start = sendTimeMilli - ONE_MINUTE;
        int count = cacheClient.zRangeByScoreCount(key, (double) start, (double) sendTimeMilli);
        if (count > LIMIT_MINUTE) {
            cacheClient.zRemove(key, String.valueOf(sendTimeMilli));
            reject(submit, mobile, ExceptionEnums.ONE_MINUTE_LIMIT, "【策略模块-一分钟限流策略】 计数超限，无法发送！");
        }
    }

    private void hourLimit(StandardSubmit submit, Long clientId, String mobile, long sendTimeMilli) {
        String key = CacheConstant.LIMIT_HOURS + clientId + CacheConstant.SEPARATE + mobile;
        long member = tryInsertWithRetry(key, sendTimeMilli, ExceptionEnums.ONE_HOUR_LIMIT, submit, mobile,
                "【策略模块-一小时限流策略】 插入失败，满足一小时限流规则，无法发送！");

        long start = member - ONE_HOUR;
        int count = cacheClient.zRangeByScoreCount(key, (double) start, (double) member);
        if (count > LIMIT_HOUR) {
            cacheClient.zRemove(key, String.valueOf(member));
            reject(submit, mobile, ExceptionEnums.ONE_HOUR_LIMIT, "【策略模块-一小时限流策略】 计数超限，无法发送！");
        }
    }

    private void dayLimit(StandardSubmit submit, Long clientId, String mobile, long sendTimeMilli) {
        String key = CacheConstant.LIMIT_DAYS + clientId + CacheConstant.SEPARATE + mobile;
        long member = tryInsertWithRetry(key, sendTimeMilli, ExceptionEnums.ONE_DAY_LIMIT, submit, mobile,
                "【策略模块-一天限流策略】 插入失败，满足一天限流规则，无法发送！");

        long start = member - ONE_DAY;
        int count = cacheClient.zRangeByScoreCount(key, (double) start, (double) member);
        if (count > LIMIT_DAY) {
            cacheClient.zRemove(key, String.valueOf(member));
            reject(submit, mobile, ExceptionEnums.ONE_DAY_LIMIT, "【策略模块-一天限流策略】 计数超限，无法发送！");
        }
    }

    private long tryInsertWithRetry(String key,
                                    long initialMember,
                                    ExceptionEnums exceptionEnums,
                                    StandardSubmit submit,
                                    String mobile,
                                    String failLog) {
        long member = initialMember;
        for (int retry = 0; retry <= RETRY_COUNT; retry++) {
            if (Boolean.TRUE.equals(cacheClient.zadd(key, member, member))) {
                return member;
            }
            member = System.currentTimeMillis() + retry + 1;
        }
        reject(submit, mobile, exceptionEnums, failLog);
        return member;
    }

    private void reject(StandardSubmit submit, String mobile, ExceptionEnums exceptionEnums, String logMsg) {
        log.info(logMsg);
        submit.setErrorMsg(exceptionEnums + ",mobile = " + mobile);
        sendMsgUtil.sendWriteLog(submit);
        sendMsgUtil.sendPushReport(submit);
        throw new StrategyException(exceptionEnums);
    }
}
