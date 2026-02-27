package com.cz.monitor.client;


import com.cz.monitor.config.CacheFeignAuthConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.Set;

@FeignClient(value = "beacon-cache", configuration = CacheFeignAuthConfig.class)
public interface CacheClient {

    @GetMapping(value = "/cache/keys")
    Set<String> keys(@RequestParam("pattern") String pattern);

    @GetMapping("/cache/hgetall/{key}")
    Map hGetAll(@PathVariable(value = "key")String key);
}
