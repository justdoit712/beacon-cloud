package com.cz.webmaster.service;

public interface ClientBalanceDebitService {

    class DebitResult {
        private final boolean success;
        private final Long balance;
        private final Long amountLimit;
        private final String message;

        public DebitResult(boolean success, Long balance, Long amountLimit, String message) {
            this.success = success;
            this.balance = balance;
            this.amountLimit = amountLimit;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public Long getBalance() {
            return balance;
        }

        public Long getAmountLimit() {
            return amountLimit;
        }

        public String getMessage() {
            return message;
        }
    }

    DebitResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId);
}

