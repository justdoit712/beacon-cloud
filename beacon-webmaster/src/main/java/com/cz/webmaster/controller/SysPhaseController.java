package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.service.PhaseService;
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
@RequestMapping("/sys")
public class SysPhaseController {

    private final PhaseService phaseService;

    public SysPhaseController(PhaseService phaseService) {
        this.phaseService = phaseService;
    }

    @GetMapping("/phase/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        PhaseService.PageResult result = phaseService.list(keyword, offset, limit);
        return Result.ok(result.getTotal(), result.getRows());
    }

    @GetMapping("/phase/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("phase", phaseService.info(id));
        return result;
    }

    @PostMapping("/phase/save")
    public ResultVO save(@RequestBody Map<String, Object> body) {
        String errorMsg = phaseService.validateForSave(body);
        if (errorMsg != null) {
            return Result.error(errorMsg);
        }
        boolean success = phaseService.save(body, OperatorContextUtils.currentOperatorId());
        return success ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/phase/update")
    public ResultVO update(@RequestBody Map<String, Object> body) {
        String errorMsg = phaseService.validateForUpdate(body);
        if (errorMsg != null) {
            return Result.error(errorMsg);
        }
        boolean success = phaseService.update(body, OperatorContextUtils.currentOperatorId());
        return success ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/phase/del")
    public ResultVO del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        boolean success = phaseService.deleteBatch(ids);
        return success ? Result.ok("delete success") : Result.error("delete failed");
    }

    @GetMapping("/provs/all")
    public Map<String, Object> allProvs() {
        List<Map<String, Object>> sites = phaseService.allProvinces();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("sites", sites);
        result.put("data", sites);
        return result;
    }

    @GetMapping("/citys/all/{provId}")
    public Map<String, Object> allCitys(@PathVariable("provId") Long provId) {
        List<Map<String, Object>> citys = phaseService.allCities(provId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("citys", citys);
        result.put("data", citys);
        return result;
    }
}


