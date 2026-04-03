package com.cz.api.controller;

import com.cz.api.client.CacheFacade;
import com.cz.api.filter.CheckFilterContext;
import com.cz.api.form.InternalSingleSendForm;
import com.cz.api.form.SingleSendForm;
import com.cz.api.utils.Result;
import com.cz.api.vo.SmsSendResultVO;
import com.cz.common.constant.CacheKeyConstants;
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

/**
 * 短信发送入口控制器。
 *
 * <p>负责受理外部短信发送请求与内部短信发送请求，完成参数校验、
 * `StandardSubmit` 组装、基础校验链执行以及 MQ 投递。</p>
 */
@RestController
@RequestMapping("/sms")
@Slf4j
public class SmsController {

    /**
     * 真实 IP 提取时使用的请求头列表，多个头以逗号分隔。
     */
    @Value("${headers:}")
    private String headers;

    /**
     * 内部发送接口使用的调用令牌。
     *
     * <p>当配置了该值时，请求头 `X-Internal-Token` 必须与之匹配。</p>
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
    private CacheFacade cacheFacade;

    private static final String UNKNOWN = "unknown";
    private static final String X_FORWARDED_FOR = "x-forwarded-for";

    /**
     * 处理外部短信发送请求。
     *
     * <p>该入口会先执行公开接口所需的基础校验链，再将短信请求异步投递到预发送队列。</p>
     *
     * @param singleSendForm 外部发送请求体
     * @param bindingResult 参数校验结果
     * @param req 当前 HTTP 请求
     * @return 受理结果，包含 `uid` 和 `sid`
     */
    @PostMapping(value = "/single_send", produces = "application/json;charset=utf-8")
    public SmsSendResultVO singleSend(@RequestBody @Validated SingleSendForm singleSendForm,
                                      BindingResult bindingResult,
                                      HttpServletRequest req) {
        if (bindingResult.hasErrors()) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR.getCode(), firstError(bindingResult));
        }

        String realIp = getRealIp(req);
        StandardSubmit submit = buildSubmit(singleSendForm.getApikey(), null, singleSendForm.getMobile(),
                singleSendForm.getText(), singleSendForm.getState(), singleSendForm.getUid(), realIp);

        // 外部公开接口保留完整校验链。
        checkFilterContext.check(submit);
        return enqueue(submit);
    }

    /**
     * 处理内部短信发送请求。
     *
     * <p>该入口主要供平台内部系统调用，会校验内部调用令牌并补齐客户 ID 后入队。</p>
     *
     * @param form 内部发送请求体
     * @param bindingResult 参数校验结果
     * @param req 当前 HTTP 请求
     * @param requestToken 请求头中的内部调用令牌
     * @return 受理结果，包含 `uid` 和 `sid`
     */
    @PostMapping(value = "/internal/single_send", produces = "application/json;charset=utf-8")
    public SmsSendResultVO internalSingleSend(@RequestBody @Validated InternalSingleSendForm form,
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

    /**
     * 为短信请求补齐流水号和发送时间，并投递到预发送队列。
     *
     * @param submit 待投递的统一短信提交对象
     * @return 受理结果，包含业务侧 `uid` 与平台侧 `sid`
     */
    private SmsSendResultVO enqueue(StandardSubmit submit) {
        submit.setSequenceId(snowFlakeUtil.nextId());
        submit.setSendTime(LocalDateTime.now());

        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PRE_SEND, submit,
                new CorrelationData(submit.getSequenceId().toString()));

        SmsSendResultVO result = Result.ok();
        result.setUid(submit.getUid());
        result.setSid(String.valueOf(submit.getSequenceId()));
        return result;
    }

    /**
     * 构建统一的短信提交对象。
     *
     * @param apiKey 客户 apiKey
     * @param clientId 客户 ID；外部发送入口可为空
     * @param mobile 目标手机号
     * @param text 短信内容
     * @param state 短信类型
     * @param uid 客户侧请求 ID
     * @param realIp 真实请求 IP
     * @return 组装后的 `StandardSubmit`
     */
    private StandardSubmit buildSubmit(String apiKey,
                                       Long clientId,
                                       String mobile,
                                       String text,
                                       Integer state,
                                       String uid,
                                       String realIp) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        StandardSubmit submit = new StandardSubmit();
        submit.setApiKey(apiKey);
        submit.setClientId(clientId);
        submit.setMobile(mobile);
        submit.setText(text);
        submit.setState(state);
        submit.setUid(uid);
        submit.setRealIp(realIp);
        return submit;
    }

    /**
     * 根据 apiKey 从缓存中解析客户 ID。
     *
     * @param apiKey 客户 apiKey
     * @return 客户 ID；缓存未命中或格式非法时返回 {@code null}
     */
    private Long resolveClientId(String apiKey) {
        Map<String, String> clientBusiness = cacheFacade.hGetAll(CacheKeyConstants.CLIENT_BUSINESS + apiKey);
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

    /**
     * 提取首个参数校验错误信息。
     *
     * @param bindingResult 参数校验结果
     * @return 首个字段错误信息；未命中时返回通用参数错误描述
     */
    private String firstError(BindingResult bindingResult) {
        if (bindingResult.getFieldError() == null) {
            return ExceptionEnums.PARAMETER_ERROR.getMsg();
        }
        return bindingResult.getFieldError().getDefaultMessage();
    }

    /**
     * 从配置的请求头中提取真实请求 IP，未命中时回退到远端地址。
     *
     * @param req 当前 HTTP 请求
     * @return 解析得到的真实 IP
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
