package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys")
public class SysLegacyCrudController {

    private static final String FAMILY_PATTERN = "activity|apimapping|api-mapping|grayrelease|gray-release|publicparams|public-params|black|notify|searchparams|search-params|message|clientsign|client-sign|clienttemplate|client-template|apigatewayfilter|api-gateway-filter|stragetyfilter|strategy-filter|limit|smstemp|sms-template";
    private static final Map<String, String> FAMILY_ALIASES = createFamilyAliases();

    private final LegacyCrudService legacyCrudService;

    public SysLegacyCrudController(LegacyCrudService legacyCrudService) {
        this.legacyCrudService = legacyCrudService;
    }

    @GetMapping("/{family:" + FAMILY_PATTERN + "}/list")
    public PageResultVO<?> list(@PathVariable("family") String family,
                         @RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        String normalizedFamily = normalizeFamily(family);
        LegacyCrudService.PageResult result = legacyCrudService.list(normalizedFamily, keyword, offset, limit);
        return Result.ok(result.getTotal(), result.getRows());
    }

    @GetMapping("/{family:" + FAMILY_PATTERN + "}/info/{id}")
    public ResultVO<Map<String, Object>> info(@PathVariable("family") String family, @PathVariable("id") Long id) {
        return Result.ok(legacyCrudService.info(normalizeFamily(family), id));
    }

    @PostMapping("/{family:" + FAMILY_PATTERN + "}/save")
    public ResultVO<?> save(@PathVariable("family") String family, @RequestBody Map<String, Object> body) {
        String normalizedFamily = normalizeFamily(family);
        String validateError = legacyCrudService.validateForSave(normalizedFamily, body);
        if (validateError != null) {
            return Result.error(validateError);
        }
        Long operatorId = OperatorContextUtils.currentOperatorId();
        boolean success = legacyCrudService.save(normalizedFamily, body, operatorId);
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/{family:" + FAMILY_PATTERN + "}/update")
    public ResultVO<?> update(@PathVariable("family") String family, @RequestBody Map<String, Object> body) {
        String normalizedFamily = normalizeFamily(family);
        String validateError = legacyCrudService.validateForUpdate(normalizedFamily, body);
        if (validateError != null) {
            return Result.error(validateError);
        }
        Long operatorId = OperatorContextUtils.currentOperatorId();
        boolean success = legacyCrudService.update(normalizedFamily, body, operatorId);
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/{family:" + FAMILY_PATTERN + "}/del")
    public ResultVO<?> del(@PathVariable("family") String family, @RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }
        boolean success = legacyCrudService.deleteBatch(normalizeFamily(family), ids);
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }

    private String normalizeFamily(String family) {
        return FAMILY_ALIASES.getOrDefault(family, family);
    }

    private static Map<String, String> createFamilyAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("api-mapping", "apimapping");
        aliases.put("gray-release", "grayrelease");
        aliases.put("public-params", "publicparams");
        aliases.put("search-params", "searchparams");
        aliases.put("client-sign", "clientsign");
        aliases.put("client-template", "clienttemplate");
        aliases.put("api-gateway-filter", "apigatewayfilter");
        aliases.put("strategy-filter", "stragetyfilter");
        aliases.put("sms-template", "smstemp");
        return Collections.unmodifiableMap(aliases);
    }
}


