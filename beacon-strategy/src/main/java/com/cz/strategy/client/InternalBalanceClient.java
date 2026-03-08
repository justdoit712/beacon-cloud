package com.cz.strategy.client;

import com.cz.common.vo.ResultVO;
import com.cz.strategy.dto.InternalBalanceDebitRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Strategy -> Webmaster 内部余额扣减接口。
 */
@FeignClient(value = "beacon-webmaster")
public interface InternalBalanceClient {

    @PostMapping("/internal/balance/debit")
    ResultVO debit(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                   @RequestBody InternalBalanceDebitRequest request);
}
