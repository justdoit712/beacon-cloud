package com.cz.webmaster.controller;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
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

@RestController
@RequestMapping("/internal/balance")
public class InternalBalanceController {

    private static final Logger log = LoggerFactory.getLogger(InternalBalanceController.class);

    @Value("${internal.balance.token:}")
    private String internalBalanceToken;

    private final ClientBalanceDebitService clientBalanceDebitService;

    public InternalBalanceController(ClientBalanceDebitService clientBalanceDebitService) {
        this.clientBalanceDebitService = clientBalanceDebitService;
    }

    @PostMapping("/debit")
    public ResultVO debit(@RequestBody @Validated InternalBalanceDebitRequest request,
                          BindingResult bindingResult,
                          @RequestHeader(value = "X-Internal-Token", required = false) String requestToken) {
        if (bindingResult.hasErrors()) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(bindingResult));
        }
        if (StringUtils.hasText(internalBalanceToken) && !internalBalanceToken.equals(requestToken)) {
            return Result.error(-403, "internal token invalid");
        }

        try {
            ClientBalanceDebitService.DebitResult result = clientBalanceDebitService.debitAndSync(
                    request.getClientId(),
                    request.getFee(),
                    request.getAmountLimit(),
                    request.getRequestId()
            );
            if (!result.isSuccess()) {
                if ("balance not enough".equals(result.getMessage())) {
                    return Result.error(ExceptionEnums.BALANCE_NOT_ENOUGH.getCode(), ExceptionEnums.BALANCE_NOT_ENOUGH.getMsg());
                }
                return Result.error(ExceptionEnums.UNKNOWN_ERROR.getCode(), result.getMessage());
            }

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

    private String firstError(BindingResult bindingResult) {
        if (bindingResult.getFieldError() == null) {
            return ExceptionEnums.PARAMETER_ERROR.getMsg();
        }
        return bindingResult.getFieldError().getDefaultMessage();
    }
}

