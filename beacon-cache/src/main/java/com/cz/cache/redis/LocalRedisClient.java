package com.cz.cache.redis;

import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class LocalRedisClient {

    private final RedisTemplate<String, Object> redisTemplate;

    public LocalRedisClient(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void hSet(String key, Map<String, ?> map) {
        redisTemplate.opsForHash().putAll(key, map);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @SafeVarargs
    public final <T> void sAdd(String key, T... values) {
        redisTemplate.opsForSet().add(key, values);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> hGetAll(String key) {
        return (Map<String, T>) (Map<?, ?>) redisTemplate.opsForHash().entries(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String field) {
        return (T) redisTemplate.opsForHash().get(key, field);
    }

    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key) {
        return (Set<T>) (Set<?>) redisTemplate.opsForSet().members(key);
    }

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

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (key == null) {
            return null;
        }
        return (T) redisTemplate.opsForValue().get(key);
    }

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

    public <T> boolean zAdd(String key, T member, long score) {
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, member, (double) score));
    }

    public Long zRemove(String key, Object... members) {
        return redisTemplate.opsForZSet().remove(key, members);
    }

    public Long hIncrementBy(String key, String field, long delta) {
        return redisTemplate.opsForHash().increment(key, field, delta);
    }
}
