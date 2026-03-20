package com.cz.webmaster.service.impl;

import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.ClientBalanceDebitCommand;
import com.cz.webmaster.service.BalanceCommandService;
import com.cz.webmaster.service.ClientBalanceDebitService;
import org.springframework.stereotype.Service;

/**
 * {@link ClientBalanceDebitService} 的兼容实现。
 *
 * <p>该类保留原有的扣费服务入口，内部统一委托给
 * {@link BalanceCommandService} 执行真实的余额扣费命令，
 * 以避免系统中继续维护两套余额写入逻辑。</p>
 */
@Service
public class ClientBalanceDebitServiceImpl implements ClientBalanceDebitService {

    private final BalanceCommandService balanceCommandService;

    /**
     * 构造扣费兼容服务。
     *
     * @param balanceCommandService 统一余额命令服务
     */
    public ClientBalanceDebitServiceImpl(BalanceCommandService balanceCommandService) {
        this.balanceCommandService = balanceCommandService;
    }

    /**
     * 执行一次客户余额扣费。
     *
     * <p>当前实现不再自行处理扣费细节，而是直接委托统一余额命令服务执行，
     * 保证扣费、充值、调账都遵循同一套余额链路。</p>
     *
     * @param clientId 客户 id
     * @param fee 扣费金额
     * @param amountLimit 最低余额限制
     * @param requestId 请求标识
     * @return 余额命令执行结果
     */
    @Override
    public BalanceCommandResult debitAndSync(Long clientId, Long fee, Long amountLimit, String requestId) {
        ClientBalanceDebitCommand command = new ClientBalanceDebitCommand();
        command.setClientId(clientId);
        command.setFee(fee);
        command.setAmountLimit(amountLimit);
        command.setRequestId(requestId);
        return balanceCommandService.debitAndSync(command);
    }
}
