package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheConstant;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ClientBalanceUtil;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service(value = "fee")
@Slf4j
public class FeeStrategyFilter implements StrategyFilter {

    @Autowired
    private BeaconCacheClient cacheClient;

    @Autowired
    private ErrorSendMsgUtil sendMsgUtil;

    private final String BALANCE = "balance";

    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-扣费校验】   校验ing…………");
        //1、获取submit中封装的金额
        Long fee = submit.getFee();
        Long clientId = submit.getClientId();
        //2、调用Redis的decr扣减具体的金额
        Long amount = cacheClient.hIncrBy(CacheConstant.CLIENT_BALANCE + clientId, BALANCE, -fee);

        //3、获取当前客户的欠费金额的限制（外部方法调用，暂时写死为10000厘）
        Long amountLimit = ClientBalanceUtil.getClientAmountLimit(submit.getClientId());

        //4、判断扣减过后的金额，是否超出了金额限制
        if(amount < amountLimit) {
            log.info("【策略模块-扣费校验】   扣除费用后，超过欠费余额的限制，无法发送短信！！");
            //5、如果超过了，需要将扣除的费用增加回去，并且做后续处理
            cacheClient.hIncrBy(CacheConstant.CLIENT_BALANCE + clientId, BALANCE, fee);
            submit.setErrorMsg(ExceptionEnums.BALANCE_NOT_ENOUGH.getMsg());
            sendMsgUtil.sendWriteLog(submit);
            sendMsgUtil.sendPushReport(submit);
            throw new StrategyException(ExceptionEnums.BALANCE_NOT_ENOUGH);
        }
        log.info("【策略模块-扣费校验】   扣费成功！！");
    }
}