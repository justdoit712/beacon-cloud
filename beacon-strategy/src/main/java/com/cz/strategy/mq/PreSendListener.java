package com.cz.strategy.mq;

import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.filter.StrategyFilterContext;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class PreSendListener {

    /**
     * 整个策略模块的校验
     */
    @Autowired
    private StrategyFilterContext filterContext;

    @RabbitListener(queues = RabbitMQConstants.SMS_PRE_SEND)
    public void listen(StandardSubmit submit, Message message, Channel channel) throws IOException {
        log.info("【策略模块-接收消息】 接收到接口模块发送的消息 submit = {}",submit);
        // 处理业务…………
        try {
            filterContext.strategy(submit);
            log.info("【策略模块-消费完毕】手动ack");
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } catch (StrategyException e) {
            log.info("【策略模块-消费失败】未通过 msg = {}",e.getMessage());
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }
}