package com.cz.smsgateway.client;

import com.cz.smsgateway.config.CacheFeignAuthConfig;
import com.cz.common.vo.CacheStringWriteRequest;
import com.cz.common.vo.ResultVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author cz
 * @description
 */
@FeignClient(value = "beacon-cache", configuration = CacheFeignAuthConfig.class)
public interface BeaconCacheClient {

    @GetMapping("/v2/cache/hash/{key}/int/{field}")
    ResultVO<Integer> hgetIntegerTyped(@PathVariable(value = "key") String key, @PathVariable(value = "field") String field);

    @GetMapping("/v2/cache/hash/{key}/string/{field}")
    ResultVO<String> hgetTyped(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field);

    @PutMapping("/cache/strings/{key}")
    ResultVO<Void> setStringTyped(@PathVariable(value = "key") String key,
                                  @RequestBody CacheStringWriteRequest request);

    @DeleteMapping("/cache/strings/{key}")
    ResultVO<String> popStringTyped(@PathVariable(value = "key") String key);

    default Integer hgetInteger(@PathVariable(value = "key") String key, @PathVariable(value = "field") String field) {
        return unwrap(hgetIntegerTyped(key, field));
    }

    default String hget(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field) {
        return unwrap(hgetTyped(key, field));
    }

    default void setString(String key, String value, Long ttlSeconds) {
        unwrap(setStringTyped(key, new CacheStringWriteRequest(value, ttlSeconds)));
    }

    default String popString(String key) {
        return unwrap(popStringTyped(key));
    }

    static <T> T unwrap(ResultVO<T> response) {
        if (response == null) {
            return null;
        }
        Integer code = response.getCode();
        if (code != null && code != 0) {
            throw new IllegalStateException("cache call failed, code=" + code + ", msg=" + response.getMsg());
        }
        return response.getData();
    }

}
