package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.service.AcountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/acount")
public class SysAcountController {

    private final AcountService acountService;

    public SysAcountController(AcountService acountService) {
        this.acountService = acountService;
    }

    @GetMapping("/list")
    public ResultVO list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        AcountService.PageResult result = acountService.list(keyword, offset, limit);
        return Result.ok(result.getTotal(), result.getRows());
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        return Collections.singletonMap("acount", acountService.info(id));
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody Map<String, Object> body) {
        String errorMsg = acountService.validateForSave(body);
        if (errorMsg != null) {
            return Result.error(errorMsg);
        }
        boolean success = acountService.save(body, OperatorContextUtils.currentOperatorId());
        return success ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody Map<String, Object> body) {
        String errorMsg = acountService.validateForUpdate(body);
        if (errorMsg != null) {
            return Result.error(errorMsg);
        }
        boolean success = acountService.update(body, OperatorContextUtils.currentOperatorId());
        return success ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        boolean success = acountService.deleteBatch(ids);
        return success ? Result.ok("delete success") : Result.error("delete failed");
    }
}

