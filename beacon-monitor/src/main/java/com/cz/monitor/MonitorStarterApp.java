package com.cz.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;


/**
 * @author cz
 * @version V1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@Slf4j
public class MonitorStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MonitorStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-monitor started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-monitor"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = MonitorStarterApp.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }

}
