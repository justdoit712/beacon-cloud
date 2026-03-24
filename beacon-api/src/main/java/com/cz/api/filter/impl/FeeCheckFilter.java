package com.cz.api.filter.impl;

import com.cz.api.client.BeaconCacheClient;
import com.cz.api.filter.CheckFilter;
import com.cz.common.model.StandardSubmit;
import com.cz.common.constant.ApiConstant;
import com.cz.common.constant.CacheKeyConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author cz
 * @description  校验客户剩余余额是否充足。
 * <p>
 * 约束说明：
 * client_balance 的主口径为 MySQL，当前这里读取的是 Redis 余额镜像。
 * 镜像与主库的一致性由后续 Runtime Sync 链路保障。
 */
@Service(value = "fee")
@Slf4j
public class FeeCheckFilter implements CheckFilter {

    @Autowired
    private BeaconCacheClient cacheClient;

    /**
     * 只要短信内容的文字长度小于等于70个字，按照一条计算
     */
    private final int MAX_LENGTH = 70;

    /**
     * 如果短信内容的文字长度超过70，67字/条计算
     */
    private final int LOOP_LENGTH = 67;

    /**
     * Redis 余额哈希中的字段名。
     */
    private final String BALANCE = "balance";

    /**
     * 余额校验主流程：
     * 1) 计算本次短信费用；
     * 2) 读取 Redis 余额镜像；
     * 3) 判断余额是否足够，不足则抛出业务异常。
     */
    @Override
    public void check(StandardSubmit submit) {
        log.info("【接口模块-校验客户余额】   校验ing…………");
        //1、从submit中获取到短信内容
        int length = submit.getText().length();

        //2、判断短信内容的长度，如果小于等于70，算作一条，如果大于70字，按照67字/条，算出来当前短信的费用
        if(length <= MAX_LENGTH){
            // 当前短信内容是一条
            submit.setFee(ApiConstant.SINGLE_FEE);
        }else{
            int strip = length % LOOP_LENGTH == 0 ? length / LOOP_LENGTH : length / LOOP_LENGTH + 1;
            submit.setFee(ApiConstant.SINGLE_FEE * strip);
        }

        //3、从 Redis 中读取余额镜像
        // 约束说明：client_balance 的主口径为 MySQL，Redis 为派生缓存。
        // 此处读取的是缓存镜像，后续由运行时同步链路保障与 MySQL 一致。
        Integer balanceValue = cacheClient.hgetInteger(CacheKeyConstants.CLIENT_BALANCE + submit.getClientId(), BALANCE);
        Long balance = balanceValue == null ? 0L : balanceValue.longValue();

        //4、判断金额是否满足当前短信费用\
        if(balance >= submit.getFee()){
            log.info("【接口模块-校验客户余额】   用户金额充足！！");
            return;
        }

        //5、不满足就抛出异常
        log.info("【接口模块-校验客户余额】   客户余额不足");
        throw new ApiException(ExceptionEnums.BALANCE_NOT_ENOUGH);
    }


}
