package com.cz.strategy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@FeignClient(value = "beacon-cache")
public interface BeaconCacheClient {

    @GetMapping("/cache/hget/{key}/{field}")
    String hget(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field);

    @GetMapping("/cache/hget/{key}/{field}")
    Integer hgetInteger(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field);

    @GetMapping("/cache/get/{key}")
    String getString(@PathVariable(value = "key")String key);

    @PostMapping(value = "/cache/sinterstr/{key}/{sinterKey}")
    Set<Object> sinterStr(@PathVariable(value = "key")String key, @PathVariable String sinterKey, @RequestBody String... value);

    @GetMapping("/cache/smember/{key}")
    Set smember(@PathVariable(value = "key")String key);


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


}