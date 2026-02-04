package com.cz.strategy.util;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.cz.common.constant.CacheConstant;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.constant.SmsConstant;
import com.cz.common.model.StandardReport;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ErrorSendMsgUtil {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private BeaconCacheClient cacheClient;

    /**
     * 策略模块校验未通过，发送写日志操作
     * @param submit
     */
    public void sendWriteLog(StandardSubmit submit) {
        submit.setReportState(SmsConstant.REPORT_FAIL);
        // 发送消息到写日志队列
        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_WRITE_LOG, submit);
    }

    /**
     * 策略模块校验未通过，发送状态报告操作
     */

    public void sendPushReport(StandardSubmit submit) {
        Integer isCallback = cacheClient.hgetInteger(CacheConstant.CLIENT_BUSINESS + submit.getApiKey(), "isCallback");
        if(isCallback == 1){
            // 如果需要回调，再查询客户的回调地址
            String callbackUrl = cacheClient.hget(CacheConstant.CLIENT_BUSINESS + submit.getApiKey(), "callbackUrl");
            // 如果回调地址不为空
            if(!StringUtils.isEmpty(callbackUrl)){
                //客户需要状态报告推送，开始封装StandardReport
                StandardReport report = new StandardReport();
                BeanUtils.copyProperties(submit,report);
                report.setIsCallback(isCallback);
                report.setCallbackUrl(callbackUrl);
                // 发送消息到RabbitMQ
                rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PUSH_REPORT,report);
            }


        }
    }
}
