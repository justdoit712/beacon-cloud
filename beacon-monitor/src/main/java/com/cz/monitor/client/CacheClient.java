package com.cz.monitor.client;


import com.cz.monitor.config.CacheFeignAuthConfig;
import com.cz.common.vo.ResultVO;
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

    @GetMapping("/v2/cache/hash/{key}")
    ResultVO<Map<String, String>> hGetAllTyped(@PathVariable(value = "key")String key);

    @SuppressWarnings("unchecked")
    default Map hGetAll(String key) {
        return unwrap(hGetAllTyped(key));
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
