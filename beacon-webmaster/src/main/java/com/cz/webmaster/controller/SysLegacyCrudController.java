package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.service.LegacyCrudService;
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
public class SysLegacyCrudController {

    private static final String FAMILY_PATTERN = "activity|apimapping|grayrelease|publicparams|black|notify|searchparams|message|apigatewayfilter|stragetyfilter|limit|smstemp";

    private final LegacyCrudService legacyCrudService;

    public SysLegacyCrudController(LegacyCrudService legacyCrudService) {
        this.legacyCrudService = legacyCrudService;
    }

    @GetMapping("/{family:" + FAMILY_PATTERN + "}/list")
    public ResultVO list(@PathVariable("family") String family,
                         @RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        LegacyCrudService.PageResult result = legacyCrudService.list(family, keyword, offset, limit);
        return Result.ok(result.getTotal(), result.getRows());
    }

    @GetMapping("/{family:" + FAMILY_PATTERN + "}/info/{id}")
    public Map<String, Object> info(@PathVariable("family") String family, @PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        String detailKey = legacyCrudService.getDetailKey(family);
        Map<String, Object> detail = legacyCrudService.info(family, id);
        result.put(detailKey, detail);
        return result;
    }

    @PostMapping("/{family:" + FAMILY_PATTERN + "}/save")
    public ResultVO save(@PathVariable("family") String family, @RequestBody Map<String, Object> body) {
        String validateError = legacyCrudService.validateForSave(family, body);
        if (validateError != null) {
            return Result.error(validateError);
        }
        Long operatorId = OperatorContextUtils.currentOperatorId();
        boolean success = legacyCrudService.save(family, body, operatorId);
        return success ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/{family:" + FAMILY_PATTERN + "}/update")
    public ResultVO update(@PathVariable("family") String family, @RequestBody Map<String, Object> body) {
        String validateError = legacyCrudService.validateForUpdate(family, body);
        if (validateError != null) {
            return Result.error(validateError);
        }
        Long operatorId = OperatorContextUtils.currentOperatorId();
        boolean success = legacyCrudService.update(family, body, operatorId);
        return success ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/{family:" + FAMILY_PATTERN + "}/del")
    public ResultVO del(@PathVariable("family") String family, @RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        boolean success = legacyCrudService.deleteBatch(family, ids);
        return success ? Result.ok("delete success") : Result.error("delete failed");
    }
}

