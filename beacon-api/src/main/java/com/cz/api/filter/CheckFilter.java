package com.cz.api.filter;

import com.cz.common.model.model.StandardSubmit;

/**
 *
 * @author cz
 * @description 做策略模式的父接口
 */
public interface CheckFilter {

    /**
     * 校验！！！！
     * @param obj
     */
    void check(StandardSubmit submit);

}
