package com.cz.strategy.filter;

import com.cz.common.constant.CacheConstant;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class StrategyFilterContext {

    @Autowired
    private Map<String, StrategyFilter> stringStrategyFilterMap;

    @Autowired
    private BeaconCacheClient cacheClient;

    private static final String CLIENT_FILTERS = "clientFilters";
    private static final String BLACK = "black";
    private static final String BLACK_GLOBAL = "blackGlobal";
    private static final String BLACK_CLIENT = "blackClient";

    /**
     * Read strategy chain from redis and execute in configured order.
     */
    public void strategy(StandardSubmit submit) {
        String filters = cacheClient.hget(CacheConstant.CLIENT_BUSINESS + submit.getApiKey(), CLIENT_FILTERS);
        if (filters == null) {
            return;
        }

        String[] filterArray = filters.split(",");
        for (String strategy : filterArray) {
            if (strategy == null) {
                continue;
            }
            String filterName = strategy.trim();
            if (filterName.isEmpty()) {
                continue;
            }

            if (BLACK.equals(filterName)) {
                // Backward compatibility for old config value "black".
                applyFilter(BLACK_GLOBAL, submit);
                applyFilter(BLACK_CLIENT, submit);
                continue;
            }

            applyFilter(filterName, submit);
        }
    }

    private void applyFilter(String filterName, StandardSubmit submit) {
        StrategyFilter strategyFilter = stringStrategyFilterMap.get(filterName);
        if (strategyFilter == null) {
            log.warn("【策略模块】未找到过滤器定义 filterName = {}", filterName);
            return;
        }
        strategyFilter.strategy(submit);
    }
}
