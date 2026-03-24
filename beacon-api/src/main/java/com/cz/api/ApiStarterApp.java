package com.cz.api;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

import java.util.Arrays;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@ComponentScan(basePackages = {
        "com.cz.api",
        "com.cz.common"
})
@Slf4j
public class ApiStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ApiStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-api started, appName={}, port={}, profiles={}",
                environment.getProperty("spring.application.name", "beacon-api"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()));
    }
}
