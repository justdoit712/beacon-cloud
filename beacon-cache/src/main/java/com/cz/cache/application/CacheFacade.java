package com.cz.cache.application;

import com.cz.cache.controller.CacheDeleteResult;
import com.cz.cache.redis.LocalRedisClient;
import com.cz.cache.redis.NamespaceKeyResolver;
import com.cz.cache.redis.RedisScanService;
import com.cz.cache.security.CacheNamespaceProperties;
import com.cz.cache.security.CacheSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存模块应用门面。
 *
 * <p>负责把命名空间转换、扫描规则校验、批量删除汇总、集合交集等
 * 用例级逻辑从 HTTP 控制器中收敛出来。</p>
 *
 * <p>该类对上提供“缓存能力用例”，对下编排 Redis 客户端与辅助组件，
 * 从而让控制层只关注协议映射。</p>
 */
@Component
public class CacheFacade {

    private static final Logger log = LoggerFactory.getLogger(CacheFacade.class);

    private final LocalRedisClient redisClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScanService redisScanService;
    private final CacheSecurityProperties cacheSecurityProperties;
    private final CacheNamespaceProperties namespaceProperties;
    private final NamespaceKeyResolver namespaceKeyResolver;

    public CacheFacade(LocalRedisClient redisClient,
                       RedisTemplate<String, Object> redisTemplate,
                       RedisScanService redisScanService,
                       CacheSecurityProperties cacheSecurityProperties,
                       CacheNamespaceProperties namespaceProperties,
                       NamespaceKeyResolver namespaceKeyResolver) {
        this.redisClient = redisClient;
        this.redisTemplate = redisTemplate;
        this.redisScanService = redisScanService;
        this.cacheSecurityProperties = cacheSecurityProperties;
        this.namespaceProperties = namespaceProperties;
        this.namespaceKeyResolver = namespaceKeyResolver;
    }

    public void hmset(String key, Map<String, Object> map) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hmset方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, map);
        redisClient.hSet(physicalKey, map);
    }

    public void set(String key, String value) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 set方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.set(physicalKey, value);
    }

    public void sadd(String key, Map<String, Object>... value) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 sadd方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.sAdd(physicalKey, value);
    }

    public Map hGetAll(String key) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hGetAll方法，逻辑key ={}，物理key ={} ", key, physicalKey);
        Map<String, Object> value = redisClient.hGetAll(physicalKey);
        log.info("【缓存模块】 hGetAll方法，逻辑key ={} 的数据 value = {}", key, value);
        return value;
    }

    public Object hget(String key, String field) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hget方法，逻辑key ={}，物理key ={}，field = {}的数据", key, physicalKey, field);
        Object value = redisClient.hGet(physicalKey, field);
        log.info("【缓存模块】 hget方法，逻辑key ={}，field = {} 的数据 value = {}", key, field, value);
        return value;
    }

    public Set smember(String key) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 smember方法，逻辑key ={}，物理key ={}", key, physicalKey);
        Set<Object> values = redisClient.sMembers(physicalKey);
        log.info("【缓存模块】 smember方法，逻辑key ={} 的数据 value = {}", key, values);
        return values;
    }

    public void pipeline(Map<String, String> map) {
        log.info("【缓存模块】 pipelineString，逻辑key数量 ={}", map.size());
        redisClient.pipelined(operations -> {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String physicalKey = namespaceKeyResolver.toPhysicalKey(entry.getKey());
                operations.opsForValue().set(physicalKey, entry.getValue());
            }
        });
    }

    public Object get(String key) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 get方法，逻辑key ={}，物理key ={}", key, physicalKey);
        Object value = redisClient.get(physicalKey);
        log.info("【缓存模块】 get方法，逻辑key ={} 的数据 value = {}", key, value);
        return value;
    }

    public Boolean setIfAbsent(String key, String value, Long ttlSeconds) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 setIfAbsent方法，逻辑key = {}，物理key = {}，ttlSeconds = {}，value = {}",
                key, physicalKey, ttlSeconds, value);
        return redisClient.setIfAbsent(physicalKey, value, ttlSeconds == null ? 0L : ttlSeconds);
    }

    public Object pop(String key) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 pop方法，逻辑key = {}，物理key = {}", key, physicalKey);
        return redisClient.getAndDelete(physicalKey);
    }

    public Boolean deleteIfValueMatches(String key, String value) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 deleteIfValueMatches方法，逻辑key = {}，物理key = {}，value = {}",
                key, physicalKey, value);
        return redisClient.deleteIfValueMatches(physicalKey, value);
    }

    public void saddStr(String key, String... value) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 saddStr方法，逻辑key = {}，物理key = {}，存储value = {}", key, physicalKey, value);
        redisClient.sAdd(physicalKey, value);
    }

    public Set<Object> sinterStr(String key, String sinterKey, String... value) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        String physicalSinterKey = namespaceKeyResolver.toPhysicalKey(sinterKey);
        log.info("【缓存模块】 sinterStr方法，逻辑key = {}，逻辑sinterKey = {}，存储value = {}", key, sinterKey, value);
        redisClient.sAdd(physicalKey, value);
        Set<Object> result = redisTemplate.opsForSet().intersect(physicalKey, physicalSinterKey);
        redisClient.delete(physicalKey);
        return result;
    }

    public Boolean zadd(String key, Long score, Object member) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zaddLong方法，逻辑key = {}，物理key = {}，存储score = {}，存储value = {}", key, physicalKey, score, member);
        return redisClient.zAdd(physicalKey, member, score);
    }

    public int zRangeByScoreCount(String key, Double start, Double end) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zRangeByScoreCount方法，逻辑key = {}，物理key = {}，start = {}，end = {}", key, physicalKey, start, end);
        Set<ZSetOperations.TypedTuple<Object>> values = redisTemplate.opsForZSet().rangeByScoreWithScores(physicalKey, start, end);
        return values == null ? 0 : values.size();
    }

    public void zRemove(String key, String member) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 zRemove方法，逻辑key = {}，物理key = {}，member = {}", key, physicalKey, member);
        redisClient.zRemove(physicalKey, member);
    }

    public Long hIncrBy(String key, String field, Long delta) {
        String physicalKey = namespaceKeyResolver.toPhysicalKey(key);
        log.info("【缓存模块】 hIncrBy方法，自增逻辑key = {}，物理key = {}，field = {}，number = {}", key, physicalKey, field, delta);
        Long result = redisClient.hIncrementBy(physicalKey, field, delta);
        log.info("【缓存模块】 hIncrBy方法，自增逻辑key = {}，field = {}，number = {}，剩余数值为 = {}", key, field, delta, result);
        return result;
    }

    public CacheDeleteResult delete(String key) {
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

    public CacheDeleteResult deleteBatch(List<String> keys) {
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

    public Set<String> keys(String pattern, Integer count) {
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
