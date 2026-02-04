package com.cz.strategy.filter.impl;


import com.cz.common.constant.CacheConstant;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.model.StandardSubmit;
import com.cz.common.util.OperatorUtil;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.MobileOperatorUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service(value = "phase")
@Slf4j
public class PhaseStrategyFilter implements StrategyFilter {

    static final Integer MOBILE_FRONT = 7;
    private final String SEPARATE = ",";
    private final String UNKNOWN = "未知 未知，未知";

    /**
     * 校验的长度
     */
    private final int LENGTH = 2;


    @Autowired
    private BeaconCacheClient beaconCacheClient;
    @Autowired
    private MobileOperatorUtil mobileOperatorUtil;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /*@Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-号段补齐】   补全ing…………");
        //1.根据手机号前7位，查询手机号信息
        String mobile = submit.getMobile().substring(0, MOBILE_FRONT);
        String mobileInfo = beaconCacheClient.getString(CacheConstant.PHASE + mobile);
        getMobileInfo: if (StringUtils.isEmpty(mobileInfo))  {
            //2、查询不到，需要调用三方接口，查询手机号对应信息
            mobileInfo = mobileOperatorUtil.getMobileInfoBy360(mobile);
            if(!StringUtils.isEmpty(mobileInfo)){
                //3、调用三方查到信息后，发送消息到MQ，并且同步到MySQL和Redis
                rabbitTemplate.convertAndSend(RabbitMQConstants.MOBILE_AREA_OPERATOR,submit.getMobile());
                break getMobileInfo;
            }
            mobileInfo = UNKNOWN;
        }

        //4、无论是Redis还是三方接口查询到之后，封装到StandardSubmit对象中
        String[] areaAndOperator = mobileInfo.split(SEPARATE);
        if (areaAndOperator.length == LENGTH) {
            submit.setArea(areaAndOperator[0]);
            submit.setOperatorId(OperatorUtil.getOperatorIdByOperatorName(areaAndOperator[1]));
        }


    }*/
    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-号段补齐】 校验ing......");

        // 1. 获取手机号前7位
        String mobile = submit.getMobile().substring(0, MOBILE_FRONT);
        String mobileInfo = beaconCacheClient.getString(CacheConstant.PHASE + mobile);

        // 2. 缓存未命中，调用第三方接口
        if (StringUtils.isEmpty(mobileInfo)) {
            log.info("【策略模块-号段补齐】 Redis未命中，查询第三方接口 mobile={}", mobile);
            mobileInfo = mobileOperatorUtil.getMobileInfoBy360(mobile);

            if (!StringUtils.isEmpty(mobileInfo)) {
                // 2.1 查询成功，发送完整信息到 MQ (用于异步同步到 MySQL 和 Redis)
                Map<String, String> mqMap = new HashMap<>();
                mqMap.put("mobile", mobile);
                mqMap.put("info", mobileInfo);

                // 注意：这里发送的是 Map，确保你的 RabbitTemplate 配置了 Jackson2JsonMessageConverter
                rabbitTemplate.convertAndSend(RabbitMQConstants.MOBILE_AREA_OPERATOR, mqMap);
            } else {
                // 2.2 第三方也查不到，设置为未知
                mobileInfo = UNKNOWN;
                log.warn("【策略模块-号段补齐】 第三方接口查询失败 mobile={}", mobile);
            }
        }

        // 4. 解析结果并封装到 StandardSubmit
        // 格式： "云南 昆明,移动"
        String[] areaAndOperator = mobileInfo.split(SEPARATE);

        if (areaAndOperator.length == LENGTH) {
            // 设置归属地 (例如：云南 昆明)
            submit.setArea(areaAndOperator[0]);
            // 设置运营商ID (根据名称 "移动" -> 1)
            Integer operatorId = OperatorUtil.getOperatorIdByOperatorName(areaAndOperator[1]);
            // 防止运营商名称无法识别导致空指针
            submit.setOperatorId(operatorId != null ? operatorId : 0);

            log.info("【策略模块-号段补齐】 完成: area={}, operatorId={}", submit.getArea(), submit.getOperatorId());
        } else {
            log.error("【策略模块-号段补齐】 数据格式错误 mobileInfo={}", mobileInfo);
        }
    }
}
