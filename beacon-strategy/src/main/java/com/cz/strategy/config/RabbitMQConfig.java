package com.cz.strategy.config;


import com.cz.common.constant.RabbitMQConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
     * 配置 JSON 消息转换器
     * Spring 会自动发现这个 Bean，并在监听消息时使用它将 byte[] 转换为 Java 对象
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();

        // 1. 注册支持 LocalDateTime 的模块
        mapper.registerModule(new JavaTimeModule());

        // 2. 禁用“将日期序列化为时间戳”的特性
        // 这样 LocalDateTime 就会被序列化为 yyyy-MM-dd HH:mm:ss 格式的字符串
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return new Jackson2JsonMessageConverter(mapper);
    }
    /**
     * 接口模块发送消息到策略模块的队列
     * @return
     */
    @Bean
    public Queue preSendQueue(){
        return QueueBuilder.durable(RabbitMQConstants.MOBILE_AREA_OPERATOR).build();
    }

    /**
     * 写日志到Elasticsearch的队列
     * @return
     */

    @Bean
    public Queue writeLogQueue(){
        return QueueBuilder.durable(RabbitMQConstants.SMS_WRITE_LOG).build();
    }

    /**
     * 策略模块发送状态报告到接口模块的队列
     * @return
     */
    @Bean
    public Queue pushReportQueue(){
        return QueueBuilder.durable(RabbitMQConstants.SMS_PUSH_REPORT).build();
    }
}