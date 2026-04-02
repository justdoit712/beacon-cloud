package com.cz.smsgateway.runnable;

import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.constant.SmsConstant;
import com.cz.common.enums.CMPP2ResultEnums;
import com.cz.common.model.StandardReport;
import com.cz.common.model.StandardSubmit;
import com.cz.smsgateway.client.CmppStateStore;
import com.cz.smsgateway.netty4.entity.CmppSubmitResp;
import com.cz.smsgateway.util.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;

/**
 * 封装任务
 * @author zjw
 * @description
 */
@Slf4j
public class SubmitRepoRunnable implements Runnable {

    private RabbitTemplate rabbitTemplate = SpringUtil.getBeanByClass(RabbitTemplate.class);

    private CmppStateStore cmppStateStore = SpringUtil.getBeanByClass(CmppStateStore.class);

    private CmppSubmitResp submitResp;

    private final int OK = 0;

    public SubmitRepoRunnable(CmppSubmitResp submitResp) {
        this.submitResp = submitResp;
    }

    @Override
    public void run() {
        StandardReport report = null;
        //1、按 sequenceId 取回提交阶段暂存的 submit
        StandardSubmit submit = cmppStateStore.takeSubmit(submitResp.getSequenceId());
        if (submit == null) {
            log.warn("cmpp submit state miss, sequenceId={}, msgId={}, result={}",
                    submitResp.getSequenceId(), submitResp.getMsgId(), submitResp.getResult());
            return;
        }

        //2、根据运营商返回的result，确认短信状态并且封装submit
        int result = submitResp.getResult();
        if (result != OK) {
            // 到这，说明运营商的提交应答中回馈的失败的情况
            String resultMessage = CMPP2ResultEnums.messageOf(result);
            submit.setReportState(SmsConstant.REPORT_FAIL);
            submit.setErrorMsg(resultMessage);
        } else {
            // 如果没进到if中，说明运营商已经正常的接收了发送短信的任务
            //3、将submit封装为Report，并暂存到共享缓存，等待最终状态报告关联
            report = new StandardReport();
            BeanUtils.copyProperties(submit, report);
            cmppStateStore.saveDeliver(String.valueOf(submitResp.getMsgId()), report);
        }
        //4、将封装好的submit直接扔RabbitMQ中，让搜索模块记录信息
        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_WRITE_LOG,submit);
    }
}
