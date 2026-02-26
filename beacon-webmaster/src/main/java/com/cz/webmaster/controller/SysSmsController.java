package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.service.SmsManageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sys/sms")
public class SysSmsController {

    private final SmsManageService smsManageService;

    public SysSmsController(SmsManageService smsManageService) {
        this.smsManageService = smsManageService;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> body) {
        String errorMsg = smsManageService.validateForSave(body);
        if (errorMsg != null) {
            return R.error(errorMsg);
        }
        boolean success = smsManageService.save(body, OperatorContextUtils.currentOperatorId());
        return success ? R.ok("send success") : R.error("send failed");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody Map<String, Object> body) {
        String errorMsg = smsManageService.validateForUpdate(body);
        if (errorMsg != null) {
            return R.error(errorMsg);
        }
        boolean success = smsManageService.update(body, OperatorContextUtils.currentOperatorId());
        return success ? R.ok("update success") : R.error("update failed");
    }
}
