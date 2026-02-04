package com.cz.search.mq;

import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.model.StandardSubmit;
import com.cz.common.util.JsonUtil;
import com.cz.search.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Slf4j
public class SmsWriteLogListener {

    @Autowired
    private SearchService searchService;

    private final String INDEX = "sms_submit_log_";

    @RabbitListener(queues = RabbitMQConstants.SMS_WRITE_LOG)
    public void consume(StandardSubmit submit, Channel channel, Message message) throws IOException {
        //1、调用搜索模块的添加方法，完成添加操作
        log.info("接收到存储日志的信息   submit = {}",submit);
        searchService.index(INDEX + getYear(),submit.getSequenceId().toString(), JsonUtil.obj2JSON(submit));


        //2、手动ack
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    public String getYear(){
        return LocalDateTime.now().getYear() + "";
    }

}