package com.cz.smsgateway.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author cz
 * @description
 */
@FeignClient(value = "beacon-cache")
public interface BeaconCacheClient {

    @GetMapping("/cache/hget/{key}/{field}")
    Integer hgetInteger(@PathVariable(value = "key") String key, @PathVariable(value = "field") String field);

    @GetMapping("/cache/hget/{key}/{field}")
    String hget(@PathVariable(value = "key")String key, @PathVariable(value = "field")String field);

}
