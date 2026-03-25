package com.cz.test;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan(basePackages = "com.cz.test.mapper")
@Slf4j
public class TestStarter {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(TestStarter.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-test started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-test"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = TestStarter.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }
}
