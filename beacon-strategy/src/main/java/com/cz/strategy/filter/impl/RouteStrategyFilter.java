package com.cz.strategy.filter.impl;

import com.cz.common.model.model.StandardSubmit;
import com.cz.strategy.filter.StrategyFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service(value = "route")
@Slf4j
public class RouteStrategyFilter implements StrategyFilter {
    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-路由校验】   校验ing…………");
    }
}
