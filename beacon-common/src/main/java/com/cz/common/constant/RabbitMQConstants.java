package com.cz.common.constant;

/**
 * 系统内使用的 RabbitMQ 队列、交换机常量。
 *
 * @author cz
 */
public interface RabbitMQConstants {

    /** API 模块投递到策略模块的预发送队列。 */
    String SMS_PRE_SEND = "sms_pre_send_topic";

    /** 手机号归属地与运营商信息同步队列。 */
    String MOBILE_AREA_OPERATOR = "mobile_area_operator_topic";


    /** 搜索模块写入 Elasticsearch 的日志队列。 */
    String SMS_WRITE_LOG = "sms_write_log_topic";

    /** 状态报告推送队列。 */
    String SMS_PUSH_REPORT = "sms_push_report_topic" ;

    /** 策略模块投递到短信网关的队列前缀，后缀追加通道 ID。 */
    String SMS_GATEWAY = "sms_gateway_topic_";

    /** 短信网关状态更新链路使用的交换机与队列。 */
    String SMS_GATEWAY_NORMAL_EXCHANGE = "sms_gateway_normal_exchange";
    String SMS_GATEWAY_NORMAL_QUEUE = "sms_gateway_normal_queue";
    String SMS_GATEWAY_DEAD_EXCHANGE = "sms_gateway_dead_exchange";
    String SMS_GATEWAY_DEAD_QUEUE = "sms_gateway_dead_queue";
}
