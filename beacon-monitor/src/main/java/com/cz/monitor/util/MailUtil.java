package com.cz.monitor.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Component
@RefreshScope
public class MailUtil {

    @Value("${spring.mail.username}")
    private String from;

    @Value("${spring.mail.tos}")
    private String tos;

    @Resource
    private JavaMailSender javaMailSender;


    public void sendEmail(String subject,String text) throws MessagingException {
        // 构建MimeMessage对象
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        // 给邮件指定信息
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        helper.setTo(tos.split(","));
        setInfo(subject, text, helper);

        // 发送邮件
        javaMailSender.send(mimeMessage);
    }

    public void sendEmail(String to,String subject,String text) throws MessagingException {
        // 构建MimeMessage对象
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        // 给邮件指定信息
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);
        helper.setTo(to);
        setInfo(subject, text, helper);

        // 发送邮件
        javaMailSender.send(mimeMessage);
    }

    private void setInfo(String subject, String text, MimeMessageHelper helper) throws MessagingException {
        helper.setFrom(from);
        helper.setSubject(subject);
        helper.setText(text);
    }


}
