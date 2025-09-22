package com.cz.api.filter.impl;

import com.cz.api.filter.CheckFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author cz
 * @description  校验短信的签名
 */
@Service(value = "sign")
@Slf4j
public class SignCheckFilter implements CheckFilter {


    @Override
    public void check(Object obj) {
        log.info("【接口模块-校验签名】   校验ing…………");
    }
}