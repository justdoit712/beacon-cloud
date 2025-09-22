package com.cz.api;


import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiStarterApp {

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(ApiStarterApp.class, args);
    }
}
