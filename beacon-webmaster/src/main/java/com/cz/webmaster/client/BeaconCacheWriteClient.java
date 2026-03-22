package com.cz.webmaster.client;

import com.cz.webmaster.config.CacheFeignAuthConfig;
import com.cz.webmaster.dto.CacheDeleteResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * beacon-cache 写删能力 Feign 客户端。
 * <p>
 * 约束：入参 key 一律使用逻辑 key，命名空间前缀由 beacon-cache 侧统一追加。
 */
@FeignClient(value = "beacon-cache", configuration = CacheFeignAuthConfig.class)
public interface BeaconCacheWriteClient {

    /**
     * Hash 覆盖写。
     */
    @PostMapping(value = "/cache/hmset/{key}")
    void hmset(@PathVariable("key") String key, @RequestBody Map<String, Object> value);

    /**
     * String 覆盖写。
     */
    @PostMapping(value = "/cache/set/{key}")
    void set(@PathVariable("key") String key, @RequestParam("value") String value);

    /**
     * 仅当 key 不存在时写入，并设置 TTL。
     */
    @PostMapping(value = "/cache/setnx/{key}")
    Boolean setIfAbsent(@PathVariable("key") String key,
                        @RequestParam("value") String value,
                        @RequestParam("ttlSeconds") Long ttlSeconds);

    /**
     * Set 写入（对象元素）。
     */
    @PostMapping(value = "/cache/sadd/{key}")
    void sadd(@PathVariable("key") String key, @RequestBody Map<String, Object>[] values);

    /**
     * Set 写入（字符串元素）。
     */
    @PostMapping(value = "/cache/saddstr/{key}")
    void saddStr(@PathVariable("key") String key, @RequestBody String[] values);

    /**
     * 删除单个 key。
     */
    @DeleteMapping(value = "/cache/delete/{key}")
    CacheDeleteResultDTO delete(@PathVariable("key") String key);

    /**
     * 批量删除 key。
     */
    @PostMapping(value = "/cache/delete/batch")
    CacheDeleteResultDTO deleteBatch(@RequestBody List<String> keys);

    /**
     * 读取单个 key。
     */
    @GetMapping(value = "/cache/get/{key}")
    Object get(@PathVariable("key") String key);

    /**
     * 按 pattern 扫描逻辑 key 列表。
     */
    @GetMapping(value = "/cache/keys")
    Set<String> keys(@RequestParam("pattern") String pattern,
                     @RequestParam("count") Integer count);

    /**
     * 原子读取并删除单个 key。
     */
    @DeleteMapping(value = "/cache/pop/{key}")
    Object pop(@PathVariable("key") String key);

    /**
     * 仅当当前 value 与入参一致时删除 key。
     */
    @DeleteMapping(value = "/cache/delete-if-match/{key}")
    Boolean deleteIfValueMatches(@PathVariable("key") String key, @RequestParam("value") String value);
}
