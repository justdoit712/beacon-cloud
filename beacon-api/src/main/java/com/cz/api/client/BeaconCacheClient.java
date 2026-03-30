package com.cz.api.client;

import com.cz.api.config.CacheFeignAuthConfig;
import com.cz.common.vo.ResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.Set;

@FeignClient(value = "beacon-cache", configuration = CacheFeignAuthConfig.class)
public interface BeaconCacheClient {

    @GetMapping("/v2/cache/hash/{key}")
    ResultVO<Map<String, String>> hGetAllTyped(@PathVariable(value = "key")String key);

    @GetMapping("/v2/cache/hash/{key}/string/{field}")
    ResultVO<String> hgetStringTyped(@PathVariable(value = "key")String key,@PathVariable(value = "field")String field);

    @GetMapping("/v2/cache/hash/{key}/int/{field}")
    ResultVO<Integer> hgetIntegerTyped(@PathVariable(value = "key")String key,@PathVariable(value = "field")String field);

    @GetMapping("/v2/cache/set/{key}/map-members")
    ResultVO<Set<Map<String, Object>>> smemberTyped(@PathVariable(value = "key")String key);
}
