package com.cz.api.controller;

import com.cz.api.client.BeaconCacheClient;
import com.cz.api.filter.CheckFilterContext;
import com.cz.api.form.InternalSingleSendForm;
import com.cz.api.form.SingleSendForm;
import com.cz.api.utils.Result;
import com.cz.api.vo.ResultVO;
import com.cz.common.constant.CacheConstant;
import com.cz.common.constant.RabbitMQConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.model.StandardSubmit;
import com.cz.common.util.SnowFlakeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/sms")
@Slf4j
public class SmsController {

    @Value("${headers:}")
    private String headers;

    /**
     * When configured, calls to internal send endpoint must provide the same token
     * in header `X-Internal-Token`.
     */
    @Value("${internal.sms.token:}")
    private String internalSmsToken;

    @Autowired
    private CheckFilterContext checkFilterContext;

    @Autowired
    private SnowFlakeUtil snowFlakeUtil;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BeaconCacheClient cacheClient;

    private static final String UNKNOWN = "unknown";
    private static final String X_FORWARDED_FOR = "x-forwarded-for";

    @PostMapping(value = "/single_send", produces = "application/json;charset=utf-8")
    public ResultVO singleSend(@RequestBody @Validated SingleSendForm singleSendForm,
                               BindingResult bindingResult,
                               HttpServletRequest req) {
        if (bindingResult.hasErrors()) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(bindingResult));
        }

        String realIp = getRealIp(req);
        StandardSubmit submit = buildSubmit(singleSendForm.getApikey(), null, singleSendForm.getMobile(),
                singleSendForm.getText(), singleSendForm.getState(), singleSendForm.getUid(), realIp);

        // Keep original external checks for public API.
        checkFilterContext.check(submit);
        return enqueue(submit);
    }

    @PostMapping(value = "/internal/single_send", produces = "application/json;charset=utf-8")
    public ResultVO internalSingleSend(@RequestBody @Validated InternalSingleSendForm form,
                                       BindingResult bindingResult,
                                       HttpServletRequest req,
                                       @RequestHeader(value = "X-Internal-Token", required = false) String requestToken) {
        if (bindingResult.hasErrors()) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(bindingResult));
        }
        if (StringUtils.hasText(internalSmsToken) && !internalSmsToken.equals(requestToken)) {
            return Result.error(-403, "internal token invalid");
        }

        Long clientId = resolveClientId(form.getApikey());
        if (clientId == null) {
            return Result.error(ExceptionEnums.ERROR_APIKEY.getCode(), ExceptionEnums.ERROR_APIKEY.getMsg());
        }

        String realIp = StringUtils.hasText(form.getRealIp()) ? form.getRealIp() : getRealIp(req);
        StandardSubmit submit = buildSubmit(form.getApikey(), clientId, form.getMobile(), form.getText(),
                form.getState(), form.getUid(), realIp);
        return enqueue(submit);
    }

    private ResultVO enqueue(StandardSubmit submit) {
        submit.setSequenceId(snowFlakeUtil.nextId());
        submit.setSendTime(LocalDateTime.now());

        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PRE_SEND, submit,
                new CorrelationData(submit.getSequenceId().toString()));

        ResultVO result = Result.ok();
        result.setUid(submit.getUid());
        result.setSid(String.valueOf(submit.getSequenceId()));
        return result;
    }

    private StandardSubmit buildSubmit(String apiKey,
                                       Long clientId,
                                       String mobile,
                                       String text,
                                       Integer state,
                                       String uid,
                                       String realIp) {
        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey(apiKey);
        submit.setClientId(clientId);
        submit.setMobile(mobile);
        submit.setText(text);
        submit.setState(state == null ? 1 : state);
        submit.setUid(uid);
        submit.setRealIp(realIp);
        return submit;
    }

    private Long resolveClientId(String apiKey) {
        Map clientBusiness = cacheClient.hGetAll(CacheConstant.CLIENT_BUSINESS + apiKey);
        if (clientBusiness == null || clientBusiness.isEmpty()) {
            return null;
        }
        Object id = clientBusiness.get("id");
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(id));
        } catch (NumberFormatException ex) {
            log.warn("invalid client id in cache for apiKey={}, value={}", apiKey, id);
            return null;
        }
    }

    private String firstError(BindingResult bindingResult) {
        if (bindingResult.getFieldError() == null) {
            return ExceptionEnums.PARAMETER_ERROR.getMsg();
        }
        return bindingResult.getFieldError().getDefaultMessage();
    }

    /**
     * Get the real request IP from configured headers, fallback to remote address.
     */
    private String getRealIp(HttpServletRequest req) {
        if (!StringUtils.hasText(headers)) {
            return req.getRemoteAddr();
        }
        String ip;
        for (String header : headers.split(",")) {
            header = header.trim();
            if (!StringUtils.hasText(header)) {
                continue;
            }
            ip = req.getHeader(header);
            if (StringUtils.hasText(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                if (X_FORWARDED_FOR.equalsIgnoreCase(header)) {
                    int index = ip.indexOf(",");
                    if (index != -1) {
                        return ip.substring(0, index).trim();
                    }
                }
                return ip;
            }
        }
        return req.getRemoteAddr();
    }
}
