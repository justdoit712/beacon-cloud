package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientTemplateService;
import org.apache.shiro.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/sys/sms-template", "/sys/smstemp"})
public class SysSmsTemplateController {

    private final ClientTemplateService clientTemplateService;

    public SysSmsTemplateController(ClientTemplateService clientTemplateService) {
        this.clientTemplateService = clientTemplateService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                                @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                                @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        long total = clientTemplateService.countByKeyword(keyword);
        return Result.ok(total, clientTemplateService.findPage(keyword, safeOffset, safeLimit));
    }

    @GetMapping("/info/{id}")
    public ResultVO<Map<String, Object>> info(@PathVariable("id") Long id) {
        Map<String, Object> row = clientTemplateService.findById(id);
        return Result.ok(row == null ? new LinkedHashMap<>() : row);
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody Map<String, Object> body) {
        String validation = validate(body, false);
        if (validation != null) {
            return Result.error(validation);
        }
        boolean success = clientTemplateService.save(body, currentOperatorId());
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody Map<String, Object> body) {
        String validation = validate(body, true);
        if (validation != null) {
            return Result.error(validation);
        }
        boolean success = clientTemplateService.update(body, currentOperatorId());
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO<?> delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }
        boolean success = clientTemplateService.deleteBatch(ids, currentOperatorId());
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }

    private String validate(Map<String, Object> body, boolean update) {
        if (body == null || body.isEmpty()) {
            return "请求参数不能为空";
        }
        if (update && toLong(readValue(body, "id")) == null) {
            return "模板id不能为空";
        }
        if (toLong(readValue(body, "signId", "sign_id")) == null) {
            return "签名ID不能为空";
        }
        if (!StringUtils.hasText(readText(body, "templateText", "template_text", "template"))) {
            return "模板内容不能为空";
        }
        if (!inRange(toInteger(readValue(body, "templateType", "template_type")), 0, 2)) {
            return "模板类型不正确";
        }
        if (!inRange(toInteger(readValue(body, "templateState", "template_state", "status")), 0, 2)) {
            return "审核状态不正确";
        }
        if (!inRange(toInteger(readValue(body, "useId", "use_id")), 0, 2)) {
            return "使用场景不正确";
        }
        if (!StringUtils.hasText(readText(body, "useWeb", "use_web"))) {
            return "使用地址不能为空";
        }
        return null;
    }

    private Long currentOperatorId() {
        SmsUser user = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        return user == null || user.getId() == null ? null : user.getId().longValue();
    }

    private boolean inRange(Integer value, int min, int max) {
        return value != null && value >= min && value <= max;
    }

    private Object readValue(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String readText(Map<String, Object> row, String... keys) {
        Object value = readValue(row, keys);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            long parsed = ((Number) value).longValue();
            return parsed > 0 ? parsed : null;
        }
        try {
            long parsed = Long.parseLong(value.toString());
            return parsed > 0 ? parsed : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ignore) {
            return null;
        }
    }
}
