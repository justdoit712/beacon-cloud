package com.cz.cache.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class RedisScanService {

    private static final Logger log = LoggerFactory.getLogger(RedisScanService.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisScanService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Set<String> scan(String pattern, int count) {
        if (!StringUtils.hasText(pattern)) {
            return new LinkedHashSet<>();
        }
        int batchSize = count <= 0 ? 1000 : Math.min(count, 5000);
        Set<String> keys = redisTemplate.execute((RedisCallback<Set<String>>) connection -> doScan(connection, pattern, batchSize));
        return keys == null ? new LinkedHashSet<>() : keys;
    }

    private Set<String> doScan(RedisConnection connection, String pattern, int count) {
        Set<String> keys = new LinkedHashSet<>();
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(count)
                .build();

        Cursor<byte[]> cursor = connection.scan(scanOptions);
        try {
            while (cursor.hasNext()) {
                byte[] keyBytes = cursor.next();
                String key = redisTemplate.getStringSerializer().deserialize(keyBytes);
                if (StringUtils.hasText(key)) {
                    keys.add(key);
                }
            }
        } finally {
            try {
                cursor.close();
            } catch (IOException e) {
                log.warn("close redis scan cursor failed", e);
            }
        }
        return keys;
    }
}
