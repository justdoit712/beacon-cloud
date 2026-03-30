package com.cz.strategy.client;

import com.cz.strategy.config.CacheFeignAuthConfig;
import com.cz.common.vo.ResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@FeignClient(value = "beacon-cache", configuration = CacheFeignAuthConfig.class)
public interface BeaconCacheClient {

    @GetMapping("/v2/cache/hash/{key}/string/{field}")
    ResultVO<String> hgetTyped(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field);

    @GetMapping("/v2/cache/hash/{key}/int/{field}")
    ResultVO<Integer> hgetIntegerTyped(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field);

    @GetMapping("/v2/cache/string/{key}")
    ResultVO<String> getStringTyped(@PathVariable(value = "key")String key);

    @PostMapping(value = "/cache/sinterstr/{key}/{sinterKey}")
    Set<Object> sinterStr(@PathVariable(value = "key")String key, @PathVariable String sinterKey, @RequestBody String... value);

    @GetMapping("/v2/cache/set/{key}/string-members")
    ResultVO<Set<String>> smemberTyped(@PathVariable(value = "key")String key);

    @GetMapping("/v2/cache/set/{key}/map-members")
    ResultVO<Set<Map<String, Object>>> smemberMapTyped(@PathVariable(value = "key")String key);


    @PostMapping(value = "/cache/zadd/{key}/{score}/{member}")
    Boolean zadd(@PathVariable(value = "key")String key,
                        @PathVariable(value = "score")Long score,
                        @PathVariable(value = "member")Object member);

    @GetMapping(value = "/cache/zrangebyscorecount/{key}/{start}/{end}")
    int zRangeByScoreCount(@PathVariable(value = "key") String key,
                                  @PathVariable(value = "start") Double start,
                                  @PathVariable(value = "end") Double end) ;

    @DeleteMapping(value = "/cache/zremove/{key}/{member}")
    public void zRemove(@PathVariable(value = "key") String key,@PathVariable(value = "member") String member) ;


    @PostMapping(value = "/cache/hincrby/{key}/{field}/{delta}")
     Long hIncrBy(@PathVariable(value = "key") String key,
                        @PathVariable(value = "field") String field,
                        @PathVariable(value = "delta") Long delta);

    @GetMapping("/v2/cache/hash/{key}")
    ResultVO<Map<String, String>> hGetAllTyped(@PathVariable(value = "key")String key);

    @PostMapping(value = "/cache/setnx/{key}")
    Boolean setIfAbsent(@PathVariable("key") String key,
                        @RequestParam("value") String value,
                        @RequestParam(value = "ttlSeconds", defaultValue = "300") Long ttlSeconds);

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
