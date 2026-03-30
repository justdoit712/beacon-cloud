package com.cz.api.filter.impl;

import com.cz.api.client.CacheFacade;
import com.cz.api.filter.CheckFilter;
import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import com.cz.common.model.StandardSubmit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验请求 IP 是否在客户白名单内。
 */
@Service(value = "ip")
@Slf4j
public class IPCheckFilter implements CheckFilter {

    private static final String IP_ADDRESS = "ipAddress";

    @Autowired
    private CacheFacade cacheFacade;

    @Override
    public void check(StandardSubmit submit) {
        log.info("【接口模块】校验ip 正在校验");

        List<String> ipWhiteList = parseIpWhiteList(
                cacheFacade.hGetString(CacheKeyConstants.CLIENT_BUSINESS + submit.getApiKey(), IP_ADDRESS)
        );
        submit.setIp(ipWhiteList);

        if (CollectionUtils.isEmpty(ipWhiteList) || ipWhiteList.contains(submit.getRealIp())) {
            log.info("【接口模块】校验ip 客户端请求IP合法");
            return;
        }

        log.info("【接口模块】校验ip 请求的ip不在白名单内");
        throw new ApiException(ExceptionEnums.IP_NOT_WHITE);
    }

    List<String> parseIpWhiteList(String value) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(value)) {
            return result;
        }
        for (String item : value.split(",")) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            result.add(item.trim());
        }
        return result;
    }
}
