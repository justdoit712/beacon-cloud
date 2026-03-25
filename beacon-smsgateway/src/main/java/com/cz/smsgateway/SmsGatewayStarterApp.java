package com.cz.smsgateway;

import cn.hippo4j.core.enable.EnableDynamicThreadPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@SpringBootApplication
@EnableDiscoveryClient
@EnableDynamicThreadPool
@EnableFeignClients
@Slf4j
public class SmsGatewayStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SmsGatewayStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-smsgateway started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-smsgateway"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = SmsGatewayStarterApp.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }

}
