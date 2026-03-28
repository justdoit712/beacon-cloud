package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.dto.SmsSendForm;
import com.cz.webmaster.service.SmsManageService;
import com.cz.webmaster.vo.SmsBatchSendVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sys/sms")
public class SysSmsController {

    private final SmsManageService smsManageService;

    public SysSmsController(SmsManageService smsManageService) {
        this.smsManageService = smsManageService;
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody SmsSendForm form) {
        String errorMsg = smsManageService.validateForSave(form);
        if (errorMsg != null) {
            return Result.error(errorMsg);
        }
        SmsBatchSendVO summary = smsManageService.save(form, OperatorContextUtils.currentOperatorId());
        return toResult(summary);
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody SmsSendForm form) {
        String errorMsg = smsManageService.validateForUpdate(form);
        if (errorMsg != null) {
            return Result.error(errorMsg);
        }
        SmsBatchSendVO summary = smsManageService.update(form, OperatorContextUtils.currentOperatorId());
        return toResult(summary);
    }

    private ResultVO<SmsBatchSendVO> toResult(SmsBatchSendVO summary) {
        if (summary == null) {
            return new ResultVO<>(-1, "发送失败");
        }
        String message = summary.getMessage() == null ? "发送失败" : summary.getMessage();
        int code = summary.getSuccess() == null || summary.getSuccess() <= 0 ? -1 : 0;
        ResultVO<SmsBatchSendVO> resultVO = new ResultVO<>(code, message);
        resultVO.setData(summary);
        return resultVO;
    }
}

