package com.cz.smsgateway;

import cn.hippo4j.core.enable.EnableDynamicThreadPool;
import com.cz.smsgateway.netty4.NettyClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableDynamicThreadPool
@EnableFeignClients
public class SmsGatewayStarterApp {



    public static void main(String[] args) {
        SpringApplication.run(SmsGatewayStarterApp.class,args);
    }

}