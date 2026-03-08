package com.cz.cache.controller;

import com.cz.cache.redis.LocalRedisClient;
import com.cz.cache.redis.NamespaceKeyResolver;
import com.cz.cache.redis.RedisScanService;
import com.cz.cache.security.CacheNamespaceProperties;
import com.cz.cache.security.CacheSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class CacheController {
    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    @Autowired
    private LocalRedisClient redisClient;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RedisScanService redisScanService;
    @Autowired
    private CacheSecurityProperties cacheSecurityProperties;
    @Autowired
    private CacheNamespaceProperties namespaceProperties;
    @Autowired
    private NamespaceKeyResolver namespaceKeyResolver;

    @PostMapping(value = "/cache/hmset/{key}")
    public void hmset(@PathVariable(value = "key")String key, @RequestBody Map<String,Object> map){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hmset方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, map);
        redisClient.hSet(physicalKey,map);
    }

    @PostMapping(value = "/cache/set/{key}")
    public void set(@PathVariable(value = "key")String key, @RequestParam(value = "value")String value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 set方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.set(physicalKey,value);
    }

    @PostMapping(value = "/cache/sadd/{key}")
    public void sadd(@PathVariable(value = "key")String key, @RequestBody Map<String,Object>... value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 sadd方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.sAdd(physicalKey,value);
    }

    @GetMapping("/cache/hgetall/{key}")
    public Map hGetAll(@PathVariable(value = "key")String key){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hGetAll方法，逻辑key ={}，物理key ={} ", key, physicalKey);
        Map<String, Object> value = redisClient.hGetAll(physicalKey);
        log.info("【缓存模块】 hGetAll方法，逻辑key ={} 的数据 value = {}", key, value);
        return value;
    }

    @GetMapping("/cache/hget/{key}/{field}")
    public Object hget(@PathVariable(value = "key")String key,@PathVariable(value = "field")String field){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hget方法，逻辑key ={}，物理key ={}，field = {}的数据", key, physicalKey, field);
        Object value = redisClient.hGet(physicalKey,field);
        log.info("【缓存模块】 hget方法，逻辑key ={}，field = {} 的数据 value = {}", key, field, value);
        return value;
    }

    @GetMapping("/cache/smember/{key}")
    public Set smember(@PathVariable(value = "key")String key){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 smember方法，逻辑key ={}，物理key ={}", key, physicalKey);
        Set<Object> values = redisClient.sMembers(physicalKey);
        log.info("【缓存模块】 smember方法，逻辑key ={} 的数据 value = {}", key, values);
        return values;
    }

    @PostMapping("/cache/pipeline/string")
    public void pipeline(@RequestBody Map<String,String> map){
        log.info("【缓存模块】 pipelineString，逻辑key数量 ={}", map.size());
        redisClient.pipelined(operations ->{
            for(Map.Entry<String, String> entry : map.entrySet()){
                String physicalKey = namespaceKeyResolver.toPhysicalKey(entry.getKey());
                operations.opsForValue().set(physicalKey, entry.getValue());
            }
        });

    }

    @GetMapping("/cache/get/{key}")
    public Object get(@PathVariable(value = "key")String key){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 get方法，逻辑key ={}，物理key ={}", key, physicalKey);
        Object value = redisClient.get(physicalKey);
        log.info("【缓存模块】 get方法，逻辑key ={} 的数据 value = {}", key, value);
        return value;
    }


    @PostMapping(value = "/cache/saddstr/{key}")
    public void saddStr(@PathVariable(value = "key")String key, @RequestBody String... value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 saddStr方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.sAdd(physicalKey,value);
    }


    @PostMapping(value = "/cache/sinterstr/{key}/{sinterKey}")
    public Set<Object> sinterStr(@PathVariable(value = "key")String key, @PathVariable String sinterKey,@RequestBody String... value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        String physicalSinterKey = namespaceKeyResolver.toPhysicalKey(sinterKey);
        log.info("【缓存模块】 sinterStr方法，逻辑key = {}，逻辑sinterKey = {}，存储value = {}", key, sinterKey, value);
        //1、 存储数据到set集合
        redisClient.sAdd(physicalKey,value);
        //2、 需要将key和sinterKey做交集操作，并拿到返回的set
        Set<Object> result = redisTemplate.opsForSet().intersect(physicalKey, physicalSinterKey);
        //3、 将key删除
        redisClient.delete(physicalKey);
        //4、 返回交集结果
        return result;
    }

    @PostMapping(value = "/cache/zadd/{key}/{score}/{member}")
    public Boolean zadd(@PathVariable(value = "key")String key,
                        @PathVariable(value = "score")Long score,
                        @PathVariable(value = "member")Object member){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zaddLong方法，逻辑key = {}，物理key = {}，存储score = {}，存储value = {}", key, physicalKey, score, member);
        Boolean result = redisClient.zAdd(physicalKey, member, score);
        return result;
    }

    @GetMapping(value = "/cache/zrangebyscorecount/{key}/{start}/{end}")
    public int zRangeByScoreCount(@PathVariable(value = "key") String key,
                                  @PathVariable(value = "start") Double start,
                                  @PathVariable(value = "end") Double end) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zRangeByScoreCount方法，逻辑key = {}，物理key = {}，start = {}，end = {}", key, physicalKey, start, end);
        Set<ZSetOperations.TypedTuple<Object>> values = redisTemplate.opsForZSet().rangeByScoreWithScores(physicalKey, start, end);
        if(values != null){
            return values.size();
        }
        return 0;
    }

    @DeleteMapping(value = "/cache/zremove/{key}/{member}")
    public void zRemove(@PathVariable(value = "key") String key,@PathVariable(value = "member") String member) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zRemove方法，逻辑key = {}，物理key = {}，member = {}", key, physicalKey, member);
        redisClient.zRemove(physicalKey,member);
    }

    @PostMapping(value = "/cache/hincrby/{key}/{field}/{delta}")
    public Long hIncrBy(@PathVariable(value = "key") String key,
                        @PathVariable(value = "field") String field,
                        @PathVariable(value = "delta") Long delta){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hIncrBy方法，自增逻辑key = {}，物理key = {}，field = {}，number = {}", key, physicalKey, field, delta);
        Long result = redisClient.hIncrementBy(physicalKey, field, delta);
        log.info("【缓存模块】 hIncrBy方法，自增逻辑key = {}，field = {}，number = {}，剩余数值为 = {}", key, field, delta, result);
        return result;
    }

    /**
     * 删除单个逻辑 key（自动映射到当前命名空间物理 key）。
     */
    @DeleteMapping(value = "/cache/delete/{key}")
    public CacheDeleteResult delete(@PathVariable(value = "key") String key) {
        if (!StringUtils.hasText(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key must not be blank");
        }
        String logicalKey = key.trim();
        String physicalKey = namespaceKeyResolver.toPhysicalKey(logicalKey);
        CacheDeleteResult result = new CacheDeleteResult();
        result.setAttemptedCount(1);
        result.setNamespace(namespaceProperties.resolvePrefix());
        try {
            boolean deleted = redisClient.delete(physicalKey);
            result.setSuccessCount(1);
            result.setDeletedCount(deleted ? 1 : 0);
            log.info("【缓存模块】 delete方法，逻辑key = {}，物理key = {}，deleted = {}，namespace = {}",
                    logicalKey, physicalKey, deleted, result.getNamespace());
        } catch (Exception ex) {
            result.setFailedKeys(Collections.singletonList(logicalKey));
            log.error("【缓存模块】 delete方法失败，逻辑key = {}，物理key = {}，namespace = {}",
                    logicalKey, physicalKey, result.getNamespace(), ex);
        }
        return result;
    }

    /**
     * 批量删除逻辑 key（自动映射到当前命名空间物理 key）。
     */
    @PostMapping(value = "/cache/delete/batch")
    public CacheDeleteResult deleteBatch(@RequestBody List<String> keys) {
        List<String> failedKeys = new ArrayList<>();
        Set<String> normalizedLogicalKeys = normalizeLogicalKeys(keys, failedKeys);

        CacheDeleteResult result = new CacheDeleteResult();
        result.setAttemptedCount(normalizedLogicalKeys.size());
        result.setNamespace(namespaceProperties.resolvePrefix());

        long successCount = 0;
        long deletedCount = 0;
        for (String logicalKey : normalizedLogicalKeys) {
            String physicalKey = namespaceKeyResolver.toPhysicalKey(logicalKey);
            try {
                boolean deleted = redisClient.delete(physicalKey);
                successCount++;
                if (deleted) {
                    deletedCount++;
                }
            } catch (Exception ex) {
                failedKeys.add(logicalKey);
                log.error("【缓存模块】 deleteBatch方法失败，逻辑key = {}，物理key = {}，namespace = {}",
                        logicalKey, physicalKey, result.getNamespace(), ex);
            }
        }
        result.setSuccessCount(successCount);
        result.setDeletedCount(deletedCount);
        result.setFailedKeys(failedKeys);

        log.info("【缓存模块】 deleteBatch方法，attemptedCount = {}，successCount = {}，deletedCount = {}，failedCount = {}，namespace = {}",
                result.getAttemptedCount(), result.getSuccessCount(), result.getDeletedCount(), result.getFailedKeys().size(), result.getNamespace());
        return result;
    }

    @GetMapping(value = "/cache/keys")
    public Set<String> keys(@RequestParam("pattern") String pattern,
                            @RequestParam(value = "count", defaultValue = "1000") Integer count){
        String logicalPattern = namespaceKeyResolver.toLogicalPattern(pattern);
        if (!isAllowedPattern(logicalPattern)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "pattern not allowed");
        }
        String physicalPattern = namespaceKeyResolver.toPhysicalPattern(logicalPattern);
        log.info("【缓存模块】 keys方法，逻辑pattern = {}，物理pattern = {}，count = {}，namespace = {}", logicalPattern, physicalPattern, count, namespaceProperties.resolvePrefix());
        Set<String> physicalKeys = redisScanService.scan(physicalPattern, count);
        Set<String> logicalKeys = namespaceKeyResolver.toLogicalKeys(physicalKeys);
        log.info("【缓存模块】 keys方法，逻辑pattern = {}，查询出的keys数量 = {}", logicalPattern, logicalKeys.size());
        return logicalKeys;
    }

    private boolean isAllowedPattern(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return false;
        }
        List<String> allowList = cacheSecurityProperties.getKeyPatternAllowList();
        if (allowList == null || allowList.isEmpty()) {
            return false;
        }
        for (String allowed : allowList) {
            if (!StringUtils.hasText(allowed)) {
                continue;
            }
            String prefix = allowed.replace("*", "");
            if (pattern.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> normalizeLogicalKeys(List<String> keys, List<String> failedKeys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (keys == null || keys.isEmpty()) {
            return normalized;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                failedKeys.add(key);
                continue;
            }
            normalized.add(key.trim());
        }
        return normalized;
    }
}
