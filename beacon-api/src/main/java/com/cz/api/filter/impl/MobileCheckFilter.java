package com.cz.api.filter.impl;

import com.cz.api.filter.CheckFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
/**
 * @author cz
 * @description  校验手机号的格式合法性
 */
@Service(value = "mobile")
@Slf4j
public class MobileCheckFilter implements CheckFilter {


    @Override
    public void check(Object obj) {
        log.info("【接口模块-校验手机号】   校验ing…………");
    }
}