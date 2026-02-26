package com.cz.webmaster.client;

import com.cz.webmaster.dto.ApiInternalSingleSendForm;
import com.cz.webmaster.vo.ApiSmsSendResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Internal API client for triggering real SMS send flow.
 */
@FeignClient(value = "beacon-api")
public interface ApiSmsClient {

    @PostMapping("/sms/internal/single_send")
    ApiSmsSendResultVO singleSend(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                  @RequestBody ApiInternalSingleSendForm body);
}
