package com.cz.strategy.util;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.constant.SmsConstant;
import com.cz.common.model.StandardReport;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.CacheFacade;
import com.cz.strategy.client.dto.ClientBusinessSnapshot;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ErrorSendMsgUtil {
    private static final Logger log = LoggerFactory.getLogger(ErrorSendMsgUtil.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CacheFacade cacheFacade;

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
        ClientBusinessSnapshot snapshot = cacheFacade.getClientBusinessSnapshot(submit.getApiKey());
        if (!snapshot.isCallbackEnabled() || !StringUtils.hasText(snapshot.getCallbackUrl())) {
            return;
        }
        if (!cacheFacade.markPushReportDispatched(submit.getSequenceId())) {
            log.info("【策略模块-错误回传】 push report already dispatched, sequenceId={}", submit.getSequenceId());
            return;
        }
        StandardReport report = new StandardReport();
        BeanUtils.copyProperties(submit, report);
        report.setIsCallback(snapshot.getIsCallback());
        report.setCallbackUrl(snapshot.getCallbackUrl());
        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PUSH_REPORT, report);
    }
}
