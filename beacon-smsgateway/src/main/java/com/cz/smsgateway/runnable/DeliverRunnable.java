package com.cz.smsgateway.runnable;

import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.constant.SmsConstant;
import com.cz.common.enums.CMPP2DeliverEnums;
import com.cz.common.model.StandardReport;
import com.cz.common.util.CMPPDeliverMapUtil;
import com.cz.smsgateway.client.CacheFacade;
import com.cz.smsgateway.util.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * @author cz
 * @description
 */
@Slf4j
public class DeliverRunnable implements Runnable {

    private RabbitTemplate rabbitTemplate = SpringUtil.getBeanByClass(RabbitTemplate.class);

    private CacheFacade cacheFacade = SpringUtil.getBeanByClass(CacheFacade.class);

    private static final String DELIVRD = "DELIVRD";

    private long msgId;

    private String stat;

    public DeliverRunnable(long msgId, String stat) {
        this.msgId = msgId;
        this.stat = stat;
    }

    @Override
    public void run() {
        // 1) load report by msgId
        StandardReport report = CMPPDeliverMapUtil.remove(msgId + "");
        if (report == null) {
            log.warn("cmpp deliver repo miss, msgId={}, stat={}, deliverCacheSize={}",
                    msgId, stat, CMPPDeliverMapUtil.size());
            return;
        }

        // 2) resolve final delivery status
        if (!StringUtils.isEmpty(stat) && DELIVRD.equals(stat)) {
            report.setReportState(SmsConstant.REPORT_SUCCESS);
        } else {
            report.setReportState(SmsConstant.REPORT_FAIL);
            report.setErrorMsg(CMPP2DeliverEnums.descriptionOf(stat));
        }

        // 3) push callback report when enabled
        if (cacheFacade.isClientCallbackEnabled(report.getApiKey())) {
            String callbackUrl = cacheFacade.getClientCallbackUrl(report.getApiKey());
            if (!StringUtils.isEmpty(callbackUrl)) {
                report.setIsCallback(1);
                report.setCallbackUrl(callbackUrl);
                rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PUSH_REPORT, report);
            }
        }

        // 4) publish delayed update event
        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_GATEWAY_NORMAL_EXCHANGE, "", report);
    }
}