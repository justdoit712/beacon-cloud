package com.cz.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


/**
 * @author cz
 * @version V1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient

public class MonitorStarterApp {

    public static void main(String[] args) {
        SpringApplication.run(MonitorStarterApp.class,args);
    }

}
