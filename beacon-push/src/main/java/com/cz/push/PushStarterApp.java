package com.cz.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * @author cz
 * @description
 */
@SpringBootApplication
@EnableDiscoveryClient
@Slf4j
public class PushStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(PushStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-push started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-push"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = PushStarterApp.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }

}
