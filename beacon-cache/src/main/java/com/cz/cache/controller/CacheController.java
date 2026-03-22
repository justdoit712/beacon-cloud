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

/**
 * 缓存访问控制器。
 *
 * <p>统一对外暴露缓存模块的基础读写、集合操作、批量删除、键扫描以及
 * 并发协调辅助接口。控制器负责完成逻辑 key 到物理 key 的命名空间映射、
 * 安全校验和请求级日志记录，具体 Redis 操作由底层客户端执行。</p>
 *
 * <p>该类面向内部服务提供通用缓存能力，不承载上层业务语义。</p>
 */
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

    /**
     * 覆盖写入 Hash 全字段值。
     *
     * @param key 逻辑 key
     * @param map 需要写入的字段集合
     */
    @PostMapping(value = "/cache/hmset/{key}")
    public void hmset(@PathVariable(value = "key")String key, @RequestBody Map<String,Object> map){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hmset方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, map);
        redisClient.hSet(physicalKey,map);
    }

    /**
     * 覆盖写入字符串值。
     *
     * @param key 逻辑 key
     * @param value 需要写入的值
     */
    @PostMapping(value = "/cache/set/{key}")
    public void set(@PathVariable(value = "key")String key, @RequestParam(value = "value")String value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 set方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.set(physicalKey,value);
    }

    /**
     * 向 Set 中追加对象成员。
     *
     * @param key 逻辑 key
     * @param value 需要追加的对象成员列表
     */
    @PostMapping(value = "/cache/sadd/{key}")
    public void sadd(@PathVariable(value = "key")String key, @RequestBody Map<String,Object>... value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 sadd方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.sAdd(physicalKey,value);
    }

    /**
     * 读取 Hash 全字段内容。
     *
     * @param key 逻辑 key
     * @return Hash 字段映射
     */
    @GetMapping("/cache/hgetall/{key}")
    public Map hGetAll(@PathVariable(value = "key")String key){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hGetAll方法，逻辑key ={}，物理key ={} ", key, physicalKey);
        Map<String, Object> value = redisClient.hGetAll(physicalKey);
        log.info("【缓存模块】 hGetAll方法，逻辑key ={} 的数据 value = {}", key, value);
        return value;
    }

    /**
     * 读取 Hash 指定字段值。
     *
     * @param key 逻辑 key
     * @param field 字段名
     * @return 字段值；未命中时返回 {@code null}
     */
    @GetMapping("/cache/hget/{key}/{field}")
    public Object hget(@PathVariable(value = "key")String key,@PathVariable(value = "field")String field){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hget方法，逻辑key ={}，物理key ={}，field = {}的数据", key, physicalKey, field);
        Object value = redisClient.hGet(physicalKey,field);
        log.info("【缓存模块】 hget方法，逻辑key ={}，field = {} 的数据 value = {}", key, field, value);
        return value;
    }

    /**
     * 读取 Set 全部成员。
     *
     * @param key 逻辑 key
     * @return Set 成员集合
     */
    @GetMapping("/cache/smember/{key}")
    public Set smember(@PathVariable(value = "key")String key){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 smember方法，逻辑key ={}，物理key ={}", key, physicalKey);
        Set<Object> values = redisClient.sMembers(physicalKey);
        log.info("【缓存模块】 smember方法，逻辑key ={} 的数据 value = {}", key, values);
        return values;
    }

    /**
     * 以 pipeline 方式批量写入字符串键值对。
     *
     * @param map 逻辑 key 到字符串值的映射
     */
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

    /**
     * 读取指定逻辑 key 的字符串值。
     *
     * @param key 逻辑 key
     * @return key 对应的值；未命中时返回 {@code null}
     */
    @GetMapping("/cache/get/{key}")
    public Object get(@PathVariable(value = "key")String key){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 get方法，逻辑key ={}，物理key ={}", key, physicalKey);
        Object value = redisClient.get(physicalKey);
        log.info("【缓存模块】 get方法，逻辑key ={} 的数据 value = {}", key, value);
        return value;
    }

    /**
     * 仅当逻辑 key 不存在时写入值，并可选设置过期时间。
     *
     * <p>该接口主要用于分布式协调场景，例如缓存重建锁的申请。</p>
     *
     * @param key 逻辑 key
     * @param value 待写入值
     * @param ttlSeconds 过期时间（秒）；为 {@code null} 或小于等于 0 时表示不额外设置 TTL
     * @return true 表示写入成功，false 表示 key 已存在
     */
    @PostMapping(value = "/cache/setnx/{key}")
    public Boolean setIfAbsent(@PathVariable(value = "key") String key,
                               @RequestParam(value = "value") String value,
                               @RequestParam(value = "ttlSeconds", defaultValue = "300") Long ttlSeconds) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 setIfAbsent方法，逻辑key = {}，物理key = {}，ttlSeconds = {}，value = {}",
                key, physicalKey, ttlSeconds, value);
        return redisClient.setIfAbsent(physicalKey, value, ttlSeconds == null ? 0L : ttlSeconds);
    }

    /**
     * 原子读取并删除指定逻辑 key。
     *
     * <p>该接口主要用于一次性消费标记值，例如缓存重建结束后消费脏标记。</p>
     *
     * @param key 逻辑 key
     * @return 删除前的值；未命中时返回 {@code null}
     */
    @DeleteMapping(value = "/cache/pop/{key}")
    public Object pop(@PathVariable(value = "key") String key) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 pop方法，逻辑key = {}，物理key = {}", key, physicalKey);
        return redisClient.getAndDelete(physicalKey);
    }

    /**
     * 仅当当前值与期望值一致时删除指定逻辑 key。
     *
     * <p>该接口主要用于带令牌校验的安全释放场景，例如仅允许锁持有者释放重建锁。</p>
     *
     * @param key 逻辑 key
     * @param value 期望匹配的值
     * @return true 表示删除成功，false 表示 key 不存在或值不匹配
     */
    @DeleteMapping(value = "/cache/delete-if-match/{key}")
    public Boolean deleteIfValueMatches(@PathVariable(value = "key") String key,
                                        @RequestParam(value = "value") String value) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 deleteIfValueMatches方法，逻辑key = {}，物理key = {}，value = {}",
                key, physicalKey, value);
        return redisClient.deleteIfValueMatches(physicalKey, value);
    }


    /**
     * 向 Set 中追加字符串成员。
     *
     * @param key 逻辑 key
     * @param value 需要追加的字符串成员列表
     */
    @PostMapping(value = "/cache/saddstr/{key}")
    public void saddStr(@PathVariable(value = "key")String key, @RequestBody String... value){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 saddStr方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.sAdd(physicalKey,value);
    }


    /**
     * 将临时集合与目标集合求交集，并返回交集结果。
     *
     * <p>执行过程会先把请求体中的成员写入临时 key，再与指定集合做交集，
     * 最后删除临时 key。</p>
     *
     * @param key 临时逻辑 key
     * @param sinterKey 参与交集计算的目标逻辑 key
     * @param value 需要写入临时集合的成员列表
     * @return 交集结果集合
     */
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

    /**
     * 向有序集合中写入成员及分值。
     *
     * @param key 逻辑 key
     * @param score 分值
     * @param member 成员值
     * @return true 表示写入成功
     */
    @PostMapping(value = "/cache/zadd/{key}/{score}/{member}")
    public Boolean zadd(@PathVariable(value = "key")String key,
                        @PathVariable(value = "score")Long score,
                        @PathVariable(value = "member")Object member){
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zaddLong方法，逻辑key = {}，物理key = {}，存储score = {}，存储value = {}", key, physicalKey, score, member);
        Boolean result = redisClient.zAdd(physicalKey, member, score);
        return result;
    }

    /**
     * 统计有序集合在指定分值区间内的成员数量。
     *
     * @param key 逻辑 key
     * @param start 分值下界
     * @param end 分值上界
     * @return 命中的成员数量
     */
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

    /**
     * 从有序集合中移除指定成员。
     *
     * @param key 逻辑 key
     * @param member 需要移除的成员
     */
    @DeleteMapping(value = "/cache/zremove/{key}/{member}")
    public void zRemove(@PathVariable(value = "key") String key,@PathVariable(value = "member") String member) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zRemove方法，逻辑key = {}，物理key = {}，member = {}", key, physicalKey, member);
        redisClient.zRemove(physicalKey,member);
    }

    /**
     * 对 Hash 指定字段执行原子自增。
     *
     * @param key 逻辑 key
     * @param field Hash 字段名
     * @param delta 增量值
     * @return 自增后的结果值
     */
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

    /**
     * 按逻辑 pattern 扫描当前命名空间下的 key 列表。
     *
     * <p>调用前会先校验 pattern 是否命中允许列表，避免任意扫描 Redis。</p>
     *
     * @param pattern 逻辑 key pattern
     * @param count 单次 scan 建议批量大小
     * @return 命中的逻辑 key 集合
     */
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

    /**
     * 判断逻辑 pattern 是否命中允许扫描的前缀规则。
     *
     * @param pattern 逻辑 key pattern
     * @return true 表示允许扫描
     */
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

    /**
     * 规范化逻辑 key 列表，并收集非法入参。
     *
     * @param keys 原始逻辑 key 列表
     * @param failedKeys 用于收集非法 key 的输出列表
     * @return 去重后的有效逻辑 key 集合
     */
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
