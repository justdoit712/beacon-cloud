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

    @SuppressWarnings("unchecked")
    default Map hGetAll(@PathVariable(value = "key") String key) {
        return unwrap(hGetAllTyped(key));
    }

    default String hgetString(@PathVariable(value = "key") String key, @PathVariable(value = "field") String field) {
        return unwrap(hgetStringTyped(key, field));
    }

    default Integer hgetInteger(@PathVariable(value = "key") String key, @PathVariable(value = "field") String field) {
        return unwrap(hgetIntegerTyped(key, field));
    }

    @SuppressWarnings("unchecked")
    default Set<Map> smember(@PathVariable(value = "key") String key) {
        return (Set<Map>) (Set<?>) unwrap(smemberTyped(key));
    }

    static <T> T unwrap(ResultVO<T> response) {
        if (response == null) {
            return null;
        }
        Integer code = response.getCode();
        if (code != null && code != 0) {
            throw new IllegalStateException("cache v2 call failed, code=" + code + ", msg=" + response.getMsg());
        }
        return response.getData();
    }
}
