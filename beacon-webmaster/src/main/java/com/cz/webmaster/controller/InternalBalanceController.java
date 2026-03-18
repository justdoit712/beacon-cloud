package com.cz.webmaster.controller;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.BalanceCommandResult;
import com.cz.webmaster.dto.InternalBalanceDebitRequest;
import com.cz.webmaster.service.ClientBalanceDebitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部余额控制器。
 *
 * <p>用于承接系统内部发起的客户余额扣减请求，入口职责包括：</p>
 * <p>1. 校验请求参数是否合法；</p>
 * <p>2. 校验内部调用令牌；</p>
 * <p>3. 调用余额扣费服务执行扣减与缓存同步；</p>
 * <p>4. 统一返回内部接口调用结果。</p>
 */
@RestController
@RequestMapping("/internal/balance")
public class InternalBalanceController {

    private static final Logger log = LoggerFactory.getLogger(InternalBalanceController.class);

    /**
     * 内部接口访问令牌。
     *
     * <p>当配置了该值时，请求头 {@code X-Internal-Token} 必须与之匹配。</p>
     */
    @Value("${internal.balance.token:}")
    private String internalBalanceToken;

    /**
     * 客户余额扣费服务。
     */
    private final ClientBalanceDebitService clientBalanceDebitService;

    public InternalBalanceController(ClientBalanceDebitService clientBalanceDebitService) {
        this.clientBalanceDebitService = clientBalanceDebitService;
    }

    /**
     * 执行内部余额扣减。
     *
     * <p>该接口仅供内部系统调用，完成参数校验、令牌校验以及扣费执行，
     * 成功后返回最新余额和本次扣费生效的余额下限。</p>
     *
     * @param request 扣费请求参数
     * @param bindingResult 参数校验结果
     * @param requestToken 请求头中的内部访问令牌
     * @return 统一响应结果；成功时 data 中包含 clientId、balance、amountLimit、requestId
     */
    @PostMapping("/debit")
    public ResultVO debit(@RequestBody @Validated InternalBalanceDebitRequest request,
                          BindingResult bindingResult,
                          @RequestHeader(value = "X-Internal-Token", required = false) String requestToken) {
        // 优先返回参数校验错误，避免无效请求进入业务扣费流程。
        if (bindingResult.hasErrors()) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(bindingResult));
        }

        // 当系统配置了内部调用令牌时，必须校验请求头中的令牌是否一致。
        if (StringUtils.hasText(internalBalanceToken) && !internalBalanceToken.equals(requestToken)) {
            return Result.error(-403, "internal token invalid");
        }

        try {
            BalanceCommandResult result = clientBalanceDebitService.debitAndSync(
                    request.getClientId(),
                    request.getFee(),
                    request.getAmountLimit(),
                    request.getRequestId()
            );
            if (!result.isSuccess()) {
                return Result.error(result.getCode(), result.getMessage());
            }

            // 成功时回传本次扣费后的关键余额信息，方便调用方做链路确认。
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("clientId", request.getClientId());
            data.put("balance", result.getBalance());
            data.put("amountLimit", result.getAmountLimit());
            data.put("requestId", request.getRequestId());
            return Result.ok(data);
        } catch (IllegalArgumentException ex) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), ex.getMessage());
        } catch (Exception ex) {
            log.error("internal balance debit failed, clientId={}, requestId={}", request.getClientId(), request.getRequestId(), ex);
            return Result.error(ExceptionEnums.UNKNOWN_ERROR.getCode(), "internal balance debit failed");
        }
    }

    /**
     * 提取首个参数校验错误信息。
     *
     * @param bindingResult 参数校验结果
     * @return 首个字段错误信息；若不存在字段错误，则返回通用参数错误文案
     */
    private String firstError(BindingResult bindingResult) {
        if (bindingResult.getFieldError() == null) {
            return ExceptionEnums.PARAMETER_ERROR.getMsg();
        }
        return bindingResult.getFieldError().getDefaultMessage();
    }
}
