package com.cz.strategy.filter.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
import com.cz.common.model.constant.CacheConstant;
import com.cz.common.model.constant.RabbitMQConstants;
import com.cz.common.model.constant.SmsConstant;
import com.cz.common.model.enums.ExceptionEnums;
import com.cz.common.model.exception.StrategyException;
import com.cz.common.model.model.StandardReport;
import com.cz.common.model.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.config.RabbitMQConfig;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.DFAUtil;
import com.cz.strategy.util.HutoolDFAUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.cz.strategy.client.BeaconCacheClient;

import java.util.List;
import java.util.Set;

@Service(value = "hutoolDFADirtyWord")
@Slf4j
public class DirtyWordHutoolDFAStrategyFilter implements StrategyFilter {


    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private BeaconCacheClient cacheClient;
    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-敏感词校验-hutoolDFADirtyWord】   校验ing…………");
        //1、 获取短信内容
        String text = submit.getText();

        //2、 调用DFA查看敏感词
        List<String> dirtyWords = HutoolDFAUtil.getDirtyWord(text);

        //4、 根据返回的set集合，判断是否包含敏感词
        if (dirtyWords != null && dirtyWords.size() > 0) {
            // 5、 如果有敏感词，抛出异常 / 其他操作。。
            log.info("【策略模块-敏感词校验】   短信内容包含敏感词信息， dirtyWords = {}", dirtyWords);
            // 封装错误信息
            submit.setErrorMsg(ExceptionEnums.ERROR_DIRTY_WORD.getMsg() + "dirtyWords = " + dirtyWords.toString());
            submit.setReportState(SmsConstant.REPORT_FAIL);
            // 发送消息到写日志队列
            rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_WRITE_LOG,submit);
            // 发送状态报告前，需要对StandardReport进行封装
            Integer isCallback = cacheClient.hgetInteger(CacheConstant.CLIENT_BUSINESS + submit.getApiKey(), CacheConstant.IS_CALLBACK);
            if(isCallback == 1){
                // 如果需要回调，再查询客户的回调地址
                String callbackUrl = cacheClient.hget(CacheConstant.CLIENT_BUSINESS + submit.getApiKey(), CacheConstant.CALLBACK_URL);
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
            // 抛出异常
            throw new StrategyException(ExceptionEnums.ERROR_DIRTY_WORD);

        }
    }
}


