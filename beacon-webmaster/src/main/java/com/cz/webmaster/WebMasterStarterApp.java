package com.cz.webmaster;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * @author cz
 * @description
 */
@SpringBootApplication
@MapperScan(basePackages = "com.cz.webmaster.mapper")
@EnableFeignClients
@Slf4j
public class WebMasterStarterApp {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(WebMasterStarterApp.class, args);
        Environment environment = context.getEnvironment();
        log.info("beacon-webmaster started, appName={}, port={}, profiles={}, version={}",
                environment.getProperty("spring.application.name", "beacon-webmaster"),
                environment.getProperty("server.port", "8080"),
                Arrays.toString(environment.getActiveProfiles()),
                resolveVersion());
    }

    private static String resolveVersion() {
        Package starterPackage = WebMasterStarterApp.class.getPackage();
        if (starterPackage == null || starterPackage.getImplementationVersion() == null) {
            return "dev";
        }
        return starterPackage.getImplementationVersion();
    }

}
