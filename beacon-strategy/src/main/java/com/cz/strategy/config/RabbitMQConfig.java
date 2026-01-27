package com.cz.strategy.config;


import com.cz.common.model.constant.RabbitMQConstants;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;


/**
 * 构建队列&交换机信息
 * @author cz
 * @description
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 接口模块发送消息到策略模块的队列
     * @return
     */
    @Bean
    public Queue preSendQueue(){
        return QueueBuilder.durable(RabbitMQConstants.MOBILE_AREA_OPERATOR).build();
    }

    /**
     * 配置 JSON 消息转换器
     * Spring 会自动发现这个 Bean，并在监听消息时使用它将 byte[] 转换为 Java 对象
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}