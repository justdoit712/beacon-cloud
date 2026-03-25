package com.cz.search;

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
public class SearchStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SearchStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-search started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-search"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = SearchStarterApp.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }
}
