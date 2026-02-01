package com.cz.push;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * @author cz
 * @description
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PushStarterApp {

    public static void main(String[] args) {
        SpringApplication.run(PushStarterApp.class,args);
    }

}
