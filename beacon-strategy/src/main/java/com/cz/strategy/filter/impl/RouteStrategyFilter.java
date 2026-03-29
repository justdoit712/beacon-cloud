package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ChannelTransferUtil;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service(value = "route")
@Slf4j
public class RouteStrategyFilter implements StrategyFilter {

    @Autowired
    private BeaconCacheClient cacheClient;

    @Autowired
    private ErrorSendMsgUtil sendMsgUtil;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-路由校验】   校验ing…………");
        Long clientId = submit.getClientId();
        Set<Map> clientChannels = cacheClient.smemberMap(CacheKeyConstants.CLIENT_CHANNEL + clientId);

        TreeSet<Map> clientWeightChannels = new TreeSet<>(new Comparator<Map>() {
            @Override
            public int compare(Map o1, Map o2) {
                int o1Weight = Integer.parseInt(String.valueOf(o1.get("clientChannelWeight")));
                int o2Weight = Integer.parseInt(String.valueOf(o2.get("clientChannelWeight")));
                if (o2Weight != o1Weight) {
                    return o2Weight - o1Weight;
                }
                long o1ChannelId = Long.parseLong(String.valueOf(o1.get("channelId")));
                long o2ChannelId = Long.parseLong(String.valueOf(o2.get("channelId")));
                return Long.compare(o1ChannelId, o2ChannelId);
            }
        });
        if (clientChannels != null) {
            clientWeightChannels.addAll(clientChannels);
        }

        boolean ok = false;
        Map channel = null;
        Map clientChannel = null;
        for (Map clientWeightChannel : clientWeightChannels) {
            if (Integer.parseInt(String.valueOf(clientWeightChannel.get("isAvailable"))) != 0) {
                continue;
            }

            channel = cacheClient.hGetAll(CacheKeyConstants.CHANNEL + clientWeightChannel.get("channelId"));
            if (channel == null || channel.isEmpty()) {
                continue;
            }
            if (Integer.parseInt(String.valueOf(channel.get("isAvailable"))) != 0) {
                continue;
            }

            Integer channelType = Integer.parseInt(String.valueOf(channel.get("channelType")));
            if (channelType != 0 && (submit.getOperatorId() == null || !submit.getOperatorId().equals(channelType))) {
                continue;
            }

            Map transferChannel = ChannelTransferUtil.transfer(submit, channel);

            ok = true;
            clientChannel = clientWeightChannel;
            break;
        }

        if (!ok) {
            log.info("【策略模块-路由策略】   没有选择到可用的通道！！");
            submit.setErrorMsg(ExceptionEnums.NO_CHANNEL.getMsg());
            sendMsgUtil.sendWriteLog(submit);
            sendMsgUtil.sendPushReport(submit);
            throw new StrategyException(ExceptionEnums.NO_CHANNEL);
        }

        submit.setChannelId(Long.parseLong(String.valueOf(channel.get("id"))));
        submit.setSrcNumber("" + channel.get("channelNumber") + clientChannel.get("clientChannelNumber"));

        try {
            String queueName = RabbitMQConstants.SMS_GATEWAY + submit.getChannelId();
            amqpAdmin.declareQueue(QueueBuilder.durable(queueName).build());
            rabbitTemplate.convertAndSend(queueName, submit);
        } catch (AmqpException e) {
            log.info("【策略模块-路由策略】   声明通道对应队列以及发送消息时出现了问题！");
            submit.setErrorMsg(e.getMessage());
            sendMsgUtil.sendWriteLog(submit);
            sendMsgUtil.sendPushReport(submit);
            throw new StrategyException(e.getMessage(), ExceptionEnums.UNKNOWN_ERROR.getCode());
        }

    }
}
