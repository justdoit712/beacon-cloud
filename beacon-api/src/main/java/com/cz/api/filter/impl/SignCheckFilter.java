package com.cz.api.filter.impl;

import com.cz.api.client.BeaconCacheClient;
import com.cz.api.filter.CheckFilter;
import com.cz.common.model.StandardSubmit;
import com.cz.common.constant.ApiConstant;
import com.cz.common.constant.CacheConstant;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * @author cz
 * @description  校验短信的签名
 */
@Service(value = "sign")
@Slf4j
public class SignCheckFilter implements CheckFilter {

    private static final Object SIGN_ID = "id";
    @Autowired
    private BeaconCacheClient cacheClient;

    /**
     * 截取签名的开始索引
     */
    private final int SIGN_START_INDEX = 1;

    /**
     * 客户存储签名信息的字段
     */
    private final String CLIENT_SIGN_INFO = "signInfo";



    @Override
    public void check(StandardSubmit submit) {
        log.info("【接口模块-校验签名】   校验ing…………");
        //1. 判断短信内容是否携带了【】
        String text = submit.getText();
        if(!text.startsWith(ApiConstant.SIGN_PREFIX) || !text.contains(ApiConstant.SIGN_SUFFIX)){
            log.info("【接口模块-校验签名】   无可用签名 text = {}",text);
            throw new ApiException(ExceptionEnums.ERROR_SIGN);
        }
        //2. 将短信内容中的签名截取出来
        String sign = text.substring(SIGN_START_INDEX, text.indexOf(ApiConstant.SIGN_SUFFIX));
        if(StringUtils.isEmpty(sign)){
            log.info("【接口模块-校验签名】   无可用签名 text = {}",text);
            throw new ApiException(ExceptionEnums.ERROR_SIGN);
        }

        //3. 从缓存中查询出客户绑定的签名
        Set<Map> set = cacheClient.smember(CacheConstant.CLIENT_SIGN + submit.getClientId());
        if(set == null || set.size() == 0){
            log.info("【接口模块-校验签名】   无可用签名 text = {}",text);
            throw new ApiException(ExceptionEnums.ERROR_SIGN);
        }
        //4. 判断
        for (Map map : set) {
            if(sign.equals(map.get(CLIENT_SIGN_INFO))){
                // 走到这，说明匹配上了具体的签名信息
                submit.setSign(sign);
                submit.setSignId(Long.parseLong(map.get(SIGN_ID) + ""));
                log.info("【接口模块-校验签名】   找到匹配的签名 sign = {}",sign);
                return;
            }
        }

        //5. 到这，说明没有匹配的签名
        log.info("【接口模块-校验签名】   无可用签名 text = {}",text);
        throw new ApiException(ExceptionEnums.ERROR_SIGN);
    }
}