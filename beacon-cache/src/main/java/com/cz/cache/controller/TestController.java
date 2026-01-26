package com.cz.cache.controller;

import com.msb.framework.redis.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * @author cz
 * @description
 */
@RestController
public class TestController {

    @Autowired
    private RedisClient redisClient;


    // 写测试   hash结构
    @PostMapping("/test/set/{key}")
    public String set(@PathVariable String key, @RequestBody Map map){
        redisClient.hSet(key, map);
        return "ok";
    }
    // 读测试
    @GetMapping("/test/get/{key}")
    public Map get(@PathVariable String key){
        Map<String, Object> result = redisClient.hGetAll(key);
        return result;
    }

    @PostMapping("/test/pipeline")
    public String pipeline(){

        Map<String, String> map = new HashMap<>();
        map.put("123279843","河南 郑州 电信");
        map.put("111111143","河南 平顶山 电信");

        redisClient.pipelined(operations ->{
                    for(Map.Entry<String, String> entry : map.entrySet()){
                        operations.opsForValue().set(entry.getKey(), entry.getValue());
                     }
        });

        return "ok";
    }



    // 写测试   hash结构
    /*@PostMapping("/test/set/{key}")
    public String set(@PathVariable String key, @RequestBody Map map){
        redisTemplate.opsForHash().putAll(key,map);
        return "ok";
    }
    // 读测试
    @GetMapping("/test/get/{key}")
    public Map get(@PathVariable String key){
        Map<Object, Object> result = redisTemplate.opsForHash().entries(key);
        return result;
    }*/
}