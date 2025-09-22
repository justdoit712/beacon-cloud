package com.cz.api.controller;

import com.cz.api.enums.SmsCodeEnum;
import com.cz.api.form.SingleSendForm;
import com.cz.api.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.BindingResultUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cz.api.utils.R;


@RestController
@RequestMapping("/sms")
@Slf4j
public class SmsController {


    @PostMapping(value = "/single_send",produces = "application/json;charset=utf-8")
    public ResultVO singleSend(@RequestBody @Validated SingleSendForm singleSendForm, BindingResult bindingResult){
        //1. 校验参数
        if (bindingResult.hasErrors()){
            String msg = bindingResult.getFieldError().getDefaultMessage();
            log.info("【接口模块-单条短信Controller】 参数不合法 msg = {}",msg);
            return R.error(SmsCodeEnum.PARAMETER_ERROR.getCode(),msg);
        }
        //     构建标准提交对象，各种封装校验




        //     发送到MQ，交给策略模块处理


        return R.ok();
    }
}