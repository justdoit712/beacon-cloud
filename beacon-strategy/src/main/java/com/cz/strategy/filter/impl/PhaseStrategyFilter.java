package com.cz.strategy.filter.impl;


import com.cz.common.model.model.StandardSubmit;
import com.cz.strategy.filter.StrategyFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service(value = "phase")
@Slf4j
public class PhaseStrategyFilter implements StrategyFilter {
    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-号段补齐】   校验ing…………");
    }
}
