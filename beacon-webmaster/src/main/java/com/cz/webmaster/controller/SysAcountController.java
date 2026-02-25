package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.AcountService;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/acount")
public class SysAcountController {

    @Autowired
    private AcountService acountService;

    @GetMapping("/list")
    public ResultVO list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        AcountService.PageResult result = acountService.list(keyword, offset, limit);
        return R.ok(result.getTotal(), result.getRows());
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("acount", acountService.info(id));
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> body) {
        String errorMsg = acountService.validateForSave(body);
        if (errorMsg != null) {
            return R.error(errorMsg);
        }
        boolean success = acountService.save(body, currentOperatorId());
        return success ? success("save success") : R.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody Map<String, Object> body) {
        String errorMsg = acountService.validateForUpdate(body);
        if (errorMsg != null) {
            return R.error(errorMsg);
        }
        boolean success = acountService.update(body, currentOperatorId());
        return success ? success("update success") : R.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.error("ids is required");
        }
        boolean success = acountService.deleteBatch(ids);
        return success ? success("delete success") : R.error("delete failed");
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

