package com.cz.cache.redis;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis 本地访问客户端。
 *
 * <p>对 {@link RedisTemplate} 的常用访问能力做轻量封装，统一提供
 * Hash、String、Set、ZSet 以及少量 Lua 原子操作，避免上层业务直接散落
 * 在各处拼装 Redis 调用细节。</p>
 *
 * <p>该组件只负责基础读写与原子辅助能力，不承担命名空间解析、权限控制、
 * 业务路由或流程编排职责。</p>
 */
@Component
public class LocalRedisClient {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis 本地访问客户端。
     *
     * @param redisTemplate Spring Data Redis 模板对象
     */
    public LocalRedisClient(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 覆盖写入 Hash 全字段值。
     *
     * @param key Redis key
     * @param map 需要写入的字段集合
     */
    public void hSet(String key, Map<String, ?> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * 覆盖写入字符串值。
     *
     * @param key Redis key
     * @param value 需要写入的值
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 覆盖写入字符串值，并可选设置过期时间。
     *
     * @param key Redis key
     * @param value 需要写入的值
     * @param ttlSeconds 过期时间（秒）；小于等于 0 时表示不额外设置 TTL
     */
    public void set(String key, Object value, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            set(key, value);
            return;
        }
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }



    /**
     * 向 Set 中追加一个或多个成员。
     *
     * @param key Redis key
     * @param values 需要追加的成员列表
     * @param <T> 成员类型
     */
    @SafeVarargs
    public final <T> void sAdd(String key, T... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    /**
     * 读取 Hash 全字段内容。
     *
     * @param key Redis key
     * @param <T> 字段值类型
     * @return Hash 字段映射
     */
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> hGetAll(String key) {
        return (Map<String, T>) (Map<?, ?>) redisTemplate.opsForHash().entries(key);
    }

    /**
     * 读取 Hash 指定字段值。
     *
     * @param key Redis key
     * @param field 字段名
     * @param <T> 字段值类型
     * @return 字段值；未命中时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String field) {
        return (T) redisTemplate.opsForHash().get(key, field);
    }

    /**
     * 读取 Set 全部成员。
     *
     * @param key Redis key
     * @param <T> 成员类型
     * @return Set 成员集合
     */
    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key) {
        return (Set<T>) (Set<?>) redisTemplate.opsForSet().members(key);
    }

    /**
     * 以 pipeline 方式批量执行 Redis 操作。
     *
     * @param consumer pipeline 回调
     * @param <T> 返回值元素类型
     * @return pipeline 执行结果列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> pipelined(Consumer<RedisOperations<String, Object>> consumer) {
        return (List<T>) redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                consumer.accept((RedisOperations<String, Object>) operations);
                return null;
            }
        });
    }

    /**
     * 读取字符串值。
     *
     * @param key Redis key
     * @param <T> 值类型
     * @return key 对应的值；当 key 为空或未命中时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (key == null) {
            return null;
        }
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 原子读取并删除指定 key。
     *
     * @param key Redis key
     * @param <T> 值类型
     * @return 删除前的值；未命中时返回 {@code null}
     */
    public <T> T getAndDelete(String key) {
        if (key == null) {
            return null;
        }
        DefaultRedisScript<Object> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local value = redis.call('get', KEYS[1]); " +
                        "if value then redis.call('del', KEYS[1]); end; " +
                        "return value;"
        );
        script.setResultType(Object.class);
        return (T) redisTemplate.execute(script, Collections.singletonList(key));
    }

    /**
     * 仅当当前值与期望值一致时删除 key。
     *
     * @param key Redis key
     * @param value 期望匹配的值
     * @return true 表示删除成功，false 表示 key 不存在或值不匹配
     */
    public boolean deleteIfValueMatches(String key, Object value) {
        if (key == null || value == null) {
            return false;
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
        script.setResultType(Long.class);
        Long affected = redisTemplate.execute(script, Collections.singletonList(key), value);
        return affected != null && affected > 0;
    }


    /**
     * 仅当 key 不存在时写入值，并可选设置过期时间。
     *
     * @param key Redis key
     * @param value 需要写入的值
     * @param ttlSeconds 过期时间（秒）；小于等于 0 时表示不额外设置 TTL
     * @return true 表示写入成功，false 表示 key 已存在
     */
    public boolean setIfAbsent(String key, Object value, long ttlSeconds) {
        if (ttlSeconds <= 0) {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS));
    }

    /**
     * 删除一个或多个 key。
     *
     * @param keys 待删除 key 列表
     * @return true 表示删除请求执行成功且至少删除了一个 key；当入参为空时返回 true
     */
    public boolean delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return true;
        }
        if (keys.length == 1) {
            return Boolean.TRUE.equals(redisTemplate.delete(keys[0]));
        }
        Long count = redisTemplate.delete(Arrays.asList(keys));
        return count != null && count > 0;
    }

    /**
     * 向有序集合写入成员及分值。
     *
     * @param key Redis key
     * @param member 成员值
     * @param score 分值
     * @param <T> 成员类型
     * @return true 表示写入成功
     */
    public <T> boolean zAdd(String key, T member, long score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, member, (double) score));
    }

    /**
     * 从有序集合中移除一个或多个成员。
     *
     * @param key Redis key
     * @param members 需要移除的成员列表
     * @return 实际移除数量
     */
    public Long zRemove(String key, Object... members) {
        return redisTemplate.opsForZSet().remove(key, members);
    }

    /**
     * 对 Hash 指定字段执行原子增量操作。
     *
     * @param key Redis key
     * @param field Hash 字段名
     * @param delta 增量值
     * @return 增量后的结果值
     */
    public Long hIncrementBy(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }
}
