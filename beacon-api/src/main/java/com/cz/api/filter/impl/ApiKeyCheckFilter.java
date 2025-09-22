package com.cz.api.filter.impl;

import com.cz.api.filter.CheckFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author cz
 * @description  校验客户的apikey是否合法
 */
@Service(value = "apikey")
@Slf4j
public class ApiKeyCheckFilter implements CheckFilter {


    @Override
    public void check(Object obj) {
        log.info("【接口模块-校验apikey】   校验ing…………");
    }
}