package com.cz.cache;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

@SpringBootApplication
@EnableDiscoveryClient
@Slf4j
public class CacheStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CacheStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-cache started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-cache"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = CacheStarterApp.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }

}
