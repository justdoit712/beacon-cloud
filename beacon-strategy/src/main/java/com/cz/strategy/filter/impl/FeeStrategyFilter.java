package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheConstant;
import com.cz.common.constant.CacheDomainContract;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.constant.CacheSourceOfTruth;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.strategy.client.BeaconCacheClient;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ClientBalanceUtil;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service(value = "fee")
@Slf4j
/**
 * 策略模块-扣费过滤器。
 * <p>
 * 当前版本用于“第二步：主口径约束固化”阶段：
 * 1) 通过注册表约束 client_balance 主口径为 MySQL；
 * 2) 保留历史 Redis 递减逻辑以兼容现网流程；
 * 3) 通过一次性告警明确后续要迁移到“MySQL 原子扣减 + 缓存刷新”。
 */
public class FeeStrategyFilter implements StrategyFilter {

    @Autowired
    private BeaconCacheClient cacheClient;

    @Autowired
    private ErrorSendMsgUtil sendMsgUtil;

    /**
     * 余额字段名称（存储在 Redis Hash 的 field）。
     */
    private final String BALANCE = "balance";

    /**
     * 仅在进程生命周期内打印一次“主口径约束提醒”，避免每条请求刷屏。
     */
    private static final AtomicBoolean SOURCE_OF_TRUTH_WARNED = new AtomicBoolean(false);

    /**
     * 扣费策略执行入口。
     * <p>
     * 注意：本方法当前仍走 Redis 扣减（历史兼容逻辑），
     * 真实余额口径已在契约中定义为 MySQL，后续需迁移实现。
     */
    @Override
    public void strategy(StandardSubmit submit) {
        log.info("【策略模块-扣费校验】   校验ing…………");
        // 第二步（主口径约束）：
        // client_balance 的真源已经定义为 MySQL。
        // 当前实现仍是历史遗留的 Redis 直接扣减路径，仅用于兼容运行。
        // 后续第二层 Runtime Sync 需迁移为“先 MySQL 原子扣减，再刷新 Redis 镜像”。
        warnMysqlSourceOfTruthConstraintOnce();

        //1、获取submit中封装的金额
        Long fee = submit.getFee();
        Long clientId = submit.getClientId();
        //2、调用Redis的decr扣减具体的金额
        Long amount = cacheClient.hIncrBy(CacheConstant.CLIENT_BALANCE + clientId, BALANCE, -fee);

        //3、获取当前客户的欠费金额的限制（外部方法调用，暂时写死为10000厘）
        Long amountLimit = ClientBalanceUtil.getClientAmountLimit(submit.getClientId());

        //4、判断扣减过后的金额，是否超出了金额限制
        if(amount < amountLimit) {
            log.info("【策略模块-扣费校验】   扣除费用后，超过欠费余额的限制，无法发送短信！！");
            //5、如果超过了，需要将扣除的费用增加回去，并且做后续处理
            cacheClient.hIncrBy(CacheConstant.CLIENT_BALANCE + clientId, BALANCE, fee);
            submit.setErrorMsg(ExceptionEnums.BALANCE_NOT_ENOUGH.getMsg());
            sendMsgUtil.sendWriteLog(submit);
            sendMsgUtil.sendPushReport(submit);
            throw new StrategyException(ExceptionEnums.BALANCE_NOT_ENOUGH);
        }
        log.info("【策略模块-扣费校验】   扣费成功！！");
    }

    /**
     * 在服务启动后的首次调用时打印一次约束提醒，强调余额主口径为 MySQL。
     * <p>
     * 该提醒仅用于开发与排障阶段，避免团队误把 Redis 当作主账本。
     */
    private void warnMysqlSourceOfTruthConstraintOnce() {
        if (SOURCE_OF_TRUTH_WARNED.get()) {
            return;
        }
        CacheDomainContract contract = CacheDomainRegistry.get(CacheDomainRegistry.CLIENT_BALANCE);
        if (contract == null) {
            return;
        }
        if (contract.getSourceOfTruth() == CacheSourceOfTruth.MYSQL
                && SOURCE_OF_TRUTH_WARNED.compareAndSet(false, true)) {
            log.warn("【余额口径约束】client_balance 主口径 = MYSQL；当前扣费实现仍走 Redis 递减，需在 Runtime Sync 阶段迁移为 MySQL 原子扣减 + 缓存刷新。");
        }
    }
}
