package com.cz.smsgateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author zjw
 * @description
 */
@Slf4j
@RestController
public class TestController {

    @Resource
    private ThreadPoolExecutor cmppSubmitPool;

    @GetMapping("/test")
    public String test(){
        cmppSubmitPool.execute(() -> {
            log.info("test endpoint task running, thread={}", Thread.currentThread().getName());
        });
        return "ok!";
    }

}
