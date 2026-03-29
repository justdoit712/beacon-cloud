package com.cz.smsgateway.client;

import com.cz.smsgateway.config.CacheFeignAuthConfig;
import com.cz.common.vo.ResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

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

    default Integer hgetInteger(@PathVariable(value = "key") String key, @PathVariable(value = "field") String field) {
        return unwrap(hgetIntegerTyped(key, field));
    }

    default String hget(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field) {
        return unwrap(hgetTyped(key, field));
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
