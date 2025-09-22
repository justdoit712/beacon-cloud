package com.cz.api.filter.impl;

import com.cz.api.filter.CheckFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author cz
 * @description  校验短信的模板
 */
@Service(value = "template")
@Slf4j
public class TemplateCheckFilter implements CheckFilter {


    @Override
    public void check(Object obj) {
        log.info("【接口模块-校验模板】   校验ing…………");
    }
}
