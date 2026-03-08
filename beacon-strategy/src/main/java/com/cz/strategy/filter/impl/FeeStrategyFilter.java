package com.cz.strategy.filter.impl;

import com.cz.common.constant.CacheDomainContract;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.constant.CacheSourceOfTruth;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.StrategyException;
import com.cz.common.model.StandardSubmit;
import com.cz.common.vo.ResultVO;
import com.cz.strategy.client.InternalBalanceClient;
import com.cz.strategy.dto.InternalBalanceDebitRequest;
import com.cz.strategy.filter.StrategyFilter;
import com.cz.strategy.util.ClientBalanceUtil;
import com.cz.strategy.util.ErrorSendMsgUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@Service(value = "fee")
@Slf4j
public class FeeStrategyFilter implements StrategyFilter {

    private static final AtomicBoolean SOURCE_OF_TRUTH_WARNED = new AtomicBoolean(false);

    private final InternalBalanceClient internalBalanceClient;
    private final ErrorSendMsgUtil sendMsgUtil;

    @Value("${internal.balance.token:}")
    private String internalBalanceToken;

    public FeeStrategyFilter(InternalBalanceClient internalBalanceClient,
                             ErrorSendMsgUtil sendMsgUtil) {
        this.internalBalanceClient = internalBalanceClient;
        this.sendMsgUtil = sendMsgUtil;
    }

    @Override
    public void strategy(StandardSubmit submit) {
        log.info("[strategy-fee] fee check start");
        warnMysqlSourceOfTruthConstraintOnce();

        Long fee = submit.getFee();
        Long clientId = submit.getClientId();
        if (fee == null || fee <= 0 || clientId == null || clientId <= 0) {
            handleUnknownFailure(submit, ExceptionEnums.PARAMETER_ERROR.getMsg(), null);
        }

        Long amountLimit = ClientBalanceUtil.getClientAmountLimit(clientId);
        InternalBalanceDebitRequest request = buildDebitRequest(submit, clientId, fee, amountLimit);

        ResultVO result;
        try {
            result = internalBalanceClient.debit(internalBalanceToken, request);
        } catch (Exception ex) {
            handleUnknownFailure(submit, "internal balance debit invoke failed", ex);
            return;
        }

        if (result == null || result.getCode() == null) {
            handleUnknownFailure(submit, "internal balance debit response invalid", null);
            return;
        }

        if (result.getCode().equals(0)) {
            log.info("[strategy-fee] fee check success, clientId={}, fee={}", clientId, fee);
            return;
        }

        if (result.getCode().equals(ExceptionEnums.BALANCE_NOT_ENOUGH.getCode())) {
            log.info("[strategy-fee] balance not enough, clientId={}, fee={}, amountLimit={}", clientId, fee, amountLimit);
            handleBalanceNotEnough(submit);
            return;
        }

        String remoteMessage = StringUtils.hasText(result.getMsg())
                ? result.getMsg()
                : "internal balance debit failed";
        handleUnknownFailure(submit, remoteMessage, null);
    }

    private InternalBalanceDebitRequest buildDebitRequest(StandardSubmit submit,
                                                          Long clientId,
                                                          Long fee,
                                                          Long amountLimit) {
        InternalBalanceDebitRequest request = new InternalBalanceDebitRequest();
        request.setClientId(clientId);
        request.setFee(fee);
        request.setAmountLimit(amountLimit);
        request.setRequestId(resolveRequestId(submit));
        return request;
    }

    private String resolveRequestId(StandardSubmit submit) {
        if (submit == null) {
            return null;
        }
        if (submit.getSequenceId() != null) {
            return String.valueOf(submit.getSequenceId());
        }
        if (StringUtils.hasText(submit.getUid())) {
            return submit.getUid().trim();
        }
        return null;
    }

    private void handleBalanceNotEnough(StandardSubmit submit) {
        submit.setErrorMsg(ExceptionEnums.BALANCE_NOT_ENOUGH.getMsg());
        sendMsgUtil.sendWriteLog(submit);
        sendMsgUtil.sendPushReport(submit);
        throw new StrategyException(ExceptionEnums.BALANCE_NOT_ENOUGH);
    }

    private void handleUnknownFailure(StandardSubmit submit, String message, Exception ex) {
        String errorMessage = StringUtils.hasText(message) ? message : ExceptionEnums.UNKNOWN_ERROR.getMsg();
        if (ex == null) {
            log.error("[strategy-fee] fee check failed, msg={}", errorMessage);
        } else {
            log.error("[strategy-fee] fee check failed, msg={}", errorMessage, ex);
        }
        submit.setErrorMsg(errorMessage);
        sendMsgUtil.sendWriteLog(submit);
        sendMsgUtil.sendPushReport(submit);
        throw new StrategyException(errorMessage, ExceptionEnums.UNKNOWN_ERROR.getCode());
    }

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
            log.warn("[balance-source] client_balance sourceOfTruth=MYSQL, debit path should use mysql atomic update + redis refresh.");
        }
    }
}
