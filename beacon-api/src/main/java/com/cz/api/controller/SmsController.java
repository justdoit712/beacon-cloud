package com.cz.api.controller;


import com.cz.api.filter.CheckFilterContext;
import com.cz.api.form.SingleSendForm;
import com.cz.api.vo.ResultVO;
import com.cz.common.model.model.StandardSubmit;
import com.cz.common.model.constant.RabbitMQConstants;
import com.cz.common.model.enums.ExceptionEnums;
import com.cz.common.model.util.SnowFlakeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cz.api.utils.R;
import javax.servlet.http.HttpServletRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;


@RestController
@RequestMapping("/sms")
@Slf4j
public class SmsController {

    @Value("${headers}")
    private String headers;

    @Autowired
    private CheckFilterContext checkFilterContext;

    @Autowired
    private SnowFlakeUtil snowFlakeUtil;

    @Autowired
    private RabbitTemplate rabbitTemplate;


    private static final String UNKNOWN = "unknown";
    private static final String X_FORWARDED_FOR = "x-forwarded-for";


    @PostMapping(value = "/single_send",produces = "application/json;charset=utf-8")
    public ResultVO singleSend(@RequestBody @Validated SingleSendForm singleSendForm, BindingResult bindingResult, HttpServletRequest req){
        //1. 校验参数
        if (bindingResult.hasErrors()){
            String msg = bindingResult.getFieldError().getDefaultMessage();
            log.info("【接口模块-单条短信Controller】 参数不合法 msg = {}",msg);
            return R.error(ExceptionEnums.PARAMETER_ERROR.getCode(),msg);
        }
        //     获取真实ip
        String ip = getRealIP(req);

        //     构建标准提交对象，各种封装校验
        StandardSubmit submit = new StandardSubmit();
        submit.setRealIP(ip);
        submit.setApiKey(singleSendForm.getApikey());
        submit.setMobile(singleSendForm.getMobile());
        submit.setText(singleSendForm.getText());
        submit.setState(singleSendForm.getState());
        submit.setUid(singleSendForm.getUid());

        //    调用策略模式的校验链
        checkFilterContext.check(submit);

        //     基于雪花算法生成唯一id，并添加到StandardSubmit对象中
        submit.setSequenceId(snowFlakeUtil.nextId());
        //     发送到MQ，交给策略模块处理

        rabbitTemplate.convertAndSend(RabbitMQConstants.SMS_PRE_SEND,submit,new CorrelationData(submit.getSequenceId().toString()));


        return R.ok();
    }

    /**
     * 获取客户端真实的IP地址
     * @param req
     * @return
     */
    private String getRealIP(HttpServletRequest req) {
        // 1. 防御性编程：如果配置未读取到，直接返回远端地址
        if (StringUtils.isEmpty(headers)) {
            return req.getRemoteAddr();
        }
        String ip;

        // 2. 遍历配置文件中定义的所有请求头
        for (String header : headers.split(",")) {
            // 去除配置文件中可能存在的空格
            header = header.trim();
            if (StringUtils.isEmpty(header)) {
                continue;
            }
            // 基于请求头获取 IP 地址
            ip = req.getHeader(header);
            // 验证 IP 有效性：不为空，且不是 "unknown"
            if (!StringUtils.isEmpty(ip) && !UNKNOWN.equalsIgnoreCase(ip)) {
                // 特殊处理 x-forwarded-for：可能包含多个 IP，取第一个
                if (X_FORWARDED_FOR.equalsIgnoreCase(header)) {
                    // 多次反向代理后会有多个ip值，第一个ip才是真实ip
                    int index = ip.indexOf(",");
                    if (index != -1) {
                        return ip.substring(0, index).trim();
                    }
                }
                // 如果不是 x-forwarded-for 或者没有逗号，直接返回
                return ip;
            }
        }

        // 6. 如果所有请求头都无法获取有效 IP，回退到基础方式
        return req.getRemoteAddr();
    }
}