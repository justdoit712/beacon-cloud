package com.cz.smsgateway.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.cz.common.constant.RabbitMQConstants.*;

/**
 * 针对性的配置，可以采用当前方式~
 * @author cz
 * @description
 */
@Configuration
public class RabbitMQConfig {

    private final int TTL = 10000;
    private final String FANOUT_ROUTING_KEY = "";

    //  声明死信队列，需要准备普通交换机，普通队列，死信交换机，死信队列
    @Bean
    public Exchange normalExchange(){
        return ExchangeBuilder.fanoutExchange(SMS_GATEWAY_NORMAL_EXCHANGE).build();
    }

    @Bean
    public Queue normalQueue(){
        Queue queue = QueueBuilder.durable(SMS_GATEWAY_NORMAL_QUEUE)
                .withArgument("x-message-ttl",TTL)
                .withArgument("x-dead-letter-exchange",SMS_GATEWAY_DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key",FANOUT_ROUTING_KEY)
                .build();
        return queue;
    }

    @Bean
    public Binding normalBinding(Exchange normalExchange,Queue normalQueue){
        return BindingBuilder.bind(normalQueue).to(normalExchange).with("").noargs();
    }

    @Bean
    public Exchange deadExchange(){
        return ExchangeBuilder.fanoutExchange(SMS_GATEWAY_DEAD_EXCHANGE).build();
    }
    @Bean
    public Queue deadQueue(){
        return QueueBuilder.durable(SMS_GATEWAY_DEAD_QUEUE).build();
    }
    @Bean
    public Binding deadBinding(Exchange deadExchange,Queue deadQueue){
        return BindingBuilder.bind(deadQueue).to(deadExchange).with("").noargs();
    }


    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }


}
