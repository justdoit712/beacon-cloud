package com.cz.strategy.filter.impl;

import com.alibaba.cloud.commons.lang.StringUtils;
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

@Service(value = "transfer")
@Slf4j
public class TransferStrategyFilter implements StrategyFilter {

    // 代表携号转网了！
    private final Boolean TRANSFER = true;

    @Autowired
    private BeaconCacheClient cacheClient;

    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-携号转网策略】   ing…………");
        //1、获取用户手机号
        String mobile = submit.getMobile();

        //2、直接基于Redis查询携号转网信息
        String value = cacheClient.getString(CacheConstant.TRANSFER + mobile);

        //3、如果存在携号转网，设置运营商信息
        if(!StringUtils.isEmpty(value)){
            // 代表携号转网了
            submit.setOperatorId(Integer.valueOf(value));
            submit.setIsTransfer(TRANSFER);
            log.info("【策略模块-携号转网策略】   当前手机号携号转网了！");
            return;
        }

        log.info("【策略模块-携号转网策略】   未转！");

    }
}