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

    /**
     * 策略模块发送手机号归属地&运营商到后台管理模块的队列名称
     */
    String MOBILE_AREA_OPERATOR = "mobile_area_operator_topic";


    /**
     * 写日志到Elasticsearch的队列名称
     */
    String SMS_WRITE_LOG = "sms_write_log_topic";

    /**
     * 策略模块发送状态报告到接口模块的队列名称
     */
    String SMS_PUSH_REPORT = "sms_push_report_topic" ;

    /**
     * 策略模块推送消息到短信网关模块的队列前缀名称，后面需要追加通道的id
     */
    String SMS_GATEWAY = "sms_gateway_topic_";

    /**
     * 短信网关模块涉及到的私信队列需要的信息
     */
    String SMS_GATEWAY_NORMAL_EXCHANGE = "sms_gateway_normal_exchange";
    String SMS_GATEWAY_NORMAL_QUEUE = "sms_gateway_normal_queue";
    String SMS_GATEWAY_DEAD_EXCHANGE = "sms_gateway_dead_exchange";
    String SMS_GATEWAY_DEAD_QUEUE = "sms_gateway_dead_queue";

}
