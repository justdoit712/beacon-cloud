package com.cz.monitor.task;

import com.cz.common.constant.RabbitMQConstants;
import com.cz.monitor.client.CacheClient;
import com.cz.monitor.util.MailUtil;
import com.rabbitmq.client.Channel;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.Set;

/**
 * 监控队列中的消息个数，如果队列消息超过10000条，直接发送短信，通知。
 * @author cz
 * @version V1.0.0
 */
@Component
@Slf4j
public class MonitorQueueMessageCountTask {

    String text = "<h1>您的队列消息队列堆积了，队名为%s，消息个数为%s</1>";

    // 队列消息限制
    private final long MESSAGE_COUNT_LIMIT = 10000;

    // 查询队列名称的固定pattern
    private final String QUEUE_PATTERN = "channel:*";

    // 得到需要截取channelID的索引地址
    private final int CHANNEL_ID_INDEX = QUEUE_PATTERN.indexOf("*");

    @Value("${spring.mail.username}")
    private String from;

    @Value("${spring.mail.tos}")
    private String tos;

    // 注入RabbitMQ的ConnectionFactory
    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private CacheClient cacheClient;

    @Autowired
    private MailUtil mailUtil;

    @XxlJob("monitorQueueMessageCountTask")
    public void monitor() throws MessagingException {
        //1、拿到所有的队列名称
        Set<String> keys = cacheClient.keys(QUEUE_PATTERN);

        //2、需要基于channel去操作
        Connection connection = connectionFactory.createConnection();
        Channel channel = connection.createChannel(false);
        listenQueueAndSendEmail(channel,RabbitMQConstants.SMS_PRE_SEND);
        for (String key : keys) {
            // 封装队列名称
            String queueName = RabbitMQConstants.SMS_GATEWAY + key.substring(CHANNEL_ID_INDEX);
            listenQueueAndSendEmail(channel, queueName);
        }




    }

    private void listenQueueAndSendEmail(Channel channel, String queueName) throws MessagingException {
        // 队列不存在，直接构建，如果已经存在，直接忽略
        try {
            channel.queueDeclare(queueName,true,false,false,null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //3、拿到对应队列的消息，确认消息数量，超过限制，及时通知

        long count = 0;
        try {
            count = channel.messageCount(queueName);
        } catch (IOException e) {
            log.error("获取队列[{}]消息数量失败", queueName, e);
            return; // 获取不到数量，直接结束本次循环
        }

        if (count > MESSAGE_COUNT_LIMIT) {
            String subject = queueName + " 队列消息堆积告警";
            String content = String.format(text, queueName, count);

            try {
                // 【第一步】先执行发送动作
                // 如果这里报错，程序会直接跳到下面的 catch 块，不会执行下一行 log
                mailUtil.sendEmail(subject, content);

                // 【第二步】如果没有报错，说明发送成功，此时再打印成功日志
                log.info("✅ 邮件发送成功 | 队列: {} | 积压: {} | 发送人: {} | 接收人: {}",
                        queueName, count, from, tos);

            } catch (Exception e) {
                // 【第三步】捕获异常，打印失败日志和堆栈信息
                log.error("❌ 邮件发送失败 | 队列: {} | 原因: {}", queueName, e.getMessage(), e);
            }
        }
    }


}
