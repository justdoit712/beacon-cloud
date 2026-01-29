package com.cz.strategy.filter.impl;

import com.cz.common.model.constant.CacheConstant;
import com.cz.common.model.enums.ExceptionEnums;
import com.cz.common.model.exception.StrategyException;
import com.cz.common.model.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service(value = "blackGlobal")
@Slf4j
public class BlackGlobalStrategyFilter implements StrategyFilter {
    @Autowired
    private ErrorSendMsgUtil sendMsgUtil;

    @Autowired
    private BeaconCacheClient cacheClient;

    // 黑名单的默认value
    private final String TRUE = "1";

    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-全局级别黑名单校验】   校验ing…………");
        //1、获取发送短信的手机号
        String mobile = submit.getMobile();

        //2、调用Redis查询
        String value = cacheClient.getString(CacheConstant.BLACK + mobile);

        //3、如果查询的结果为"1"，代表是黑名单
        if (TRUE.equals(value)) {
            log.info("【策略模块-全局级别黑名单校验】   当前手机号是黑名单！ mobile = {}", mobile);
            submit.setErrorMsg(ExceptionEnums.BLACK_GLOBAL.getMsg() + ",mobile = " + mobile);
            sendMsgUtil.sendWriteLog(submit);
            sendMsgUtil.sendPushReport(submit);
            throw new StrategyException(ExceptionEnums.BLACK_GLOBAL);
        }
        //4、不是1，正常结束
        log.info("【策略模块-全局级别黑名单校验】   当前手机号不是黑名单！");
    }


}