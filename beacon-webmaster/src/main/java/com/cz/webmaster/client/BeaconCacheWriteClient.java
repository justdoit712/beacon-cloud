package com.cz.webmaster.client;

import com.cz.webmaster.config.CacheFeignAuthConfig;
import com.cz.webmaster.dto.CacheDeleteResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

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
}

