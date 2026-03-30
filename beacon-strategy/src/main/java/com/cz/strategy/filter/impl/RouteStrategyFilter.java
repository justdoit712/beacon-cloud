package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.CacheFacade;
import com.cz.strategy.client.dto.ChannelInfo;
import com.cz.strategy.client.dto.ClientChannelBinding;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service(value = "route")
@Slf4j
public class RouteStrategyFilter implements StrategyFilter {

    @Autowired
    private CacheFacade cacheFacade;

    @Autowired
    private ErrorSendMsgUtil sendMsgUtil;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-路由校验】   校验ing…………");
        try {
            ClientChannelBinding selectedBinding = null;
            ChannelInfo selectedChannel = null;

            List<ClientChannelBinding> bindings = new ArrayList<>(cacheFacade.getClientChannelBindings(submit.getClientId()));
            bindings.sort(Comparator
                    .comparingInt(ClientChannelBinding::getWeight).reversed()
                    .thenComparingLong(ClientChannelBinding::getChannelId));

            for (ClientChannelBinding binding : bindings) {
                if (!binding.isAvailableForRoute()) {
                    continue;
                }

                ChannelInfo channel = cacheFacade.getChannelInfo(binding.getChannelId());
                if (channel == null || !channel.isAvailableForRoute()) {
                    continue;
                }
                if (!channel.supportsOperator(submit.getOperatorId())) {
                    continue;
                }

                selectedBinding = binding;
                selectedChannel = channel;
                break;
            }

            if (selectedBinding == null || selectedChannel == null) {
                fail(submit, ExceptionEnums.NO_CHANNEL, ExceptionEnums.NO_CHANNEL.getMsg());
            }

            submit.setChannelId(selectedChannel.getId());
            submit.setSrcNumber("" + selectedChannel.getChannelNumber() + selectedBinding.getClientChannelNumber());

            String queueName = RabbitMQConstants.SMS_GATEWAY + submit.getChannelId();
            amqpAdmin.declareQueue(QueueBuilder.durable(queueName).build());
            rabbitTemplate.convertAndSend(queueName, submit);
        } catch (AmqpException e) {
            log.info("【策略模块-路由策略】   声明通道对应队列以及发送消息时出现了问题！");
            submit.setErrorMsg(e.getMessage());
            sendMsgUtil.sendWriteLog(submit);
            sendMsgUtil.sendPushReport(submit);
            throw new StrategyException(e.getMessage(), ExceptionEnums.UNKNOWN_ERROR.getCode());
        } catch (StrategyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("【策略模块-路由策略】   解析通道路由缓存失败！clientId={}", submit.getClientId(), ex);
            fail(submit, ExceptionEnums.UNKNOWN_ERROR, "route cache data invalid");
        }

    }

    private void fail(StandardSubmit submit, ExceptionEnums exception, String errorMsg) {
        submit.setErrorMsg(errorMsg);
        sendMsgUtil.sendWriteLog(submit);
        sendMsgUtil.sendPushReport(submit);
        throw new StrategyException(exception);
    }
}
