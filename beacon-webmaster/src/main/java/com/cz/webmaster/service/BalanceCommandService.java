package com.cz.webmaster.service;

import com.cz.webmaster.dto.BalanceCommandResult;

/**
 * Unified balance command service backed by {@code client_balance}.
 */
public interface BalanceCommandService {

    BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId);

    BalanceCommandResult rechargeAndSync(Long clientId, Long amount, Long updateId, String requestId);

    BalanceCommandResult adjustAndSync(Long clientId, Long delta, Long amountLimit, Long updateId, String requestId);
}
