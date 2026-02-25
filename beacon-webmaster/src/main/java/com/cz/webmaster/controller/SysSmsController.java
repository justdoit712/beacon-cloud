package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.SmsManageService;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sys/sms")
public class SysSmsController {

    @Autowired
    private SmsManageService smsManageService;

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> body) {
        String errorMsg = smsManageService.validateForSave(body);
        if (errorMsg != null) {
            return R.error(errorMsg);
        }
        boolean success = smsManageService.save(body, currentOperatorId());
        return success ? success("send success") : R.error("send failed");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody Map<String, Object> body) {
        String errorMsg = smsManageService.validateForUpdate(body);
        if (errorMsg != null) {
            return R.error(errorMsg);
        }
        boolean success = smsManageService.update(body, currentOperatorId());
        return success ? success("update success") : R.error("update failed");
    }

    private ResultVO success(String msg) {
        ResultVO vo = R.ok();
        vo.setMsg(msg);
        return vo;
    }

    private Long currentOperatorId() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof SmsUser)) {
            return null;
        }
        SmsUser user = (SmsUser) principal;
        return user.getId() == null ? null : user.getId().longValue();
    }
}

