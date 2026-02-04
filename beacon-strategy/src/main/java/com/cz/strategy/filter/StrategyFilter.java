package com.cz.strategy.filter;

import com.cz.common.model.StandardSubmit;

public interface StrategyFilter {

    /**
     * 校验！！！！
     * @param submit
     */
    void strategy(StandardSubmit submit);
}
