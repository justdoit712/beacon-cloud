package com.cz.webmaster;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * @author cz
 * @description
 */
@SpringBootApplication
@MapperScan(basePackages = "com.cz.webmaster.mapper")
@EnableFeignClients
public class WebMasterStarterApp {

    public static void main(String[] args) {
        SpringApplication.run(WebMasterStarterApp.class, args);
    }

}
