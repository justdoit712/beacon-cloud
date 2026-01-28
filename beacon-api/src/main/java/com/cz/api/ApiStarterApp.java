package com.cz.api;


import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@ComponentScan(basePackages = {
        "com.cz.api",
        "com.cz.common"
})
public class ApiStarterApp {

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(ApiStarterApp.class, args);
        System.out.println("ApiStarterApp  mission complete");
    }
}
