package com.cz.common.model.constant;

/**common中声明
 * RabbitMQ中的一些队列信息
 * @author cz
 * @description
 */
public interface RabbitMQConstants {

    /**
     * 接口模块发送消息到策略模块的队列名称
     */
    String SMS_PRE_SEND = "sms_pre_send_topic";
}
