package com.cz.cache.controller;

import com.msb.framework.redis.RedisClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@Slf4j
public class CacheController {
    @Autowired
    private RedisClient redisClient;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping(value = "/cache/hmset/{key}")
    public void hmset(@PathVariable(value = "key")String key, @RequestBody Map<String,Object> map){
        log.info("【缓存模块】 hmset方法，存储key = {}，存储value = {}",key,map);
        redisClient.hSet(key,map);
    }

    @PostMapping(value = "/cache/set/{key}")
    public void set(@PathVariable(value = "key")String key, @RequestParam(value = "value")String value){
        log.info("【缓存模块】 set方法，存储key = {}，存储value = {}",key,value);
        redisClient.set(key,value);
    }

    @PostMapping(value = "/cache/sadd/{key}")
    public void sadd(@PathVariable(value = "key")String key, @RequestBody Map<String,Object>... value){
        log.info("【缓存模块】 sadd方法，存储key = {}，存储value = {}", key, value);
        redisClient.sAdd(key,value);
    }

    @GetMapping("/cache/hgetall/{key}")
    public Map hGetAll(@PathVariable(value = "key")String key){
        log.info("【缓存模块】 hGetAll方法，获取key ={} 的数据", key);
        Map<String, Object> value = redisClient.hGetAll(key);
        log.info("【缓存模块】 hGetAll方法，获取key ={} 的数据 value = {}", key,value);
        return value;
    }

    @GetMapping("/cache/hget/{key}/{field}")
    public Object hget(@PathVariable(value = "key")String key,@PathVariable(value = "field")String field){
        log.info("【缓存模块】 hget方法，获取key ={}，field = {}的数据", key,field);
        Object value = redisClient.hGet(key,field);
        log.info("【缓存模块】 hGetAll方法，获取key ={}，field = {} 的数据 value = {}", key,field,value);
        return value;
    }

    @GetMapping("/cache/smember/{key}")
    public Set smember(@PathVariable(value = "key")String key){
        log.info("【缓存模块】 smember方法，获取key ={}的数据", key);
        Set<Object> values = redisClient.sMembers(key);
        log.info("【缓存模块】 smember方法，获取key ={} 的数据 value = {}", key,values);
        return values;
    }

    @PostMapping("/cache/pipeline/string")
    public void pipeline(@RequestBody Map<String,String> map){
        log.info("【缓存模块】 pipelineString，获取到存储的数据，map的长度 ={}的数据", map.size());
        redisClient.pipelined(operations ->{
            for(Map.Entry<String, String> entry : map.entrySet()){
                operations.opsForValue().set(entry.getKey(), entry.getValue());
            }
        });

    }

    @GetMapping("/cache/get/{key}")
    public Object get(@PathVariable(value = "key")String key){
        log.info("【缓存模块】 get方法，获取key ={}的数据", key);
        Object value = redisClient.get(key);
        log.info("【缓存模块】 get方法，获取key ={} 的数据 value = {}", key,value);
        return value;
    }


    @PostMapping(value = "/cache/saddstr/{key}")
    public void saddStr(@PathVariable(value = "key")String key, @RequestBody String... value){
        log.info("【缓存模块】 saddStr方法，存储key = {}，存储value = {}", key, value);
        redisClient.sAdd(key,value);
    }


    @PostMapping(value = "/cache/sinterstr/{key}/{sinterKey}")
    public Set<Object> sinterStr(@PathVariable(value = "key")String key, @PathVariable String sinterKey,@RequestBody String... value){
        log.info("【缓存模块】 sinterStr的交集方法，存储key = {}，sinterKey = {}，存储value = {}", key, sinterKey,value);
        //1、 存储数据到set集合
        redisClient.sAdd(key,value);
        //2、 需要将key和sinterKey做交集操作，并拿到返回的set
        Set<Object> result = redisTemplate.opsForSet().intersect(key, sinterKey);
        //3、 将key删除
        redisClient.delete(key);
        //4、 返回交集结果
        return result;
    }

}
