package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.entity.CodeLimit;
import com.cz.webmaster.service.CodeLimitService;
import com.cz.webmaster.vo.CodeLimitVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/limit")
public class SysCodeLimitController {

    private final CodeLimitService codeLimitService;

    public SysCodeLimitController(CodeLimitService codeLimitService) {
        this.codeLimitService = codeLimitService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "10") int limit,
                                @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = limit <= 0 ? 10 : limit;
        long total = codeLimitService.countByKeyword(keyword);
        List<CodeLimit> list = codeLimitService.findListByPage(keyword, safeOffset, safeLimit);

        List<CodeLimitVO> rows = new ArrayList<>();
        if (list != null) {
            for (CodeLimit item : list) {
                rows.add(toVO(item));
            }
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        CodeLimitVO vo = toVO(codeLimitService.findById(id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("limit", vo);
        result.put("data", vo);
        return result;
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody CodeLimitVO codeLimitVO) {
        if (!isValidForSave(codeLimitVO)) {
            return Result.error("limitTime, limitCount and description are required");
        }
        CodeLimit entity = toEntity(codeLimitVO);
        Long operatorId = OperatorContextUtils.currentOperatorId();
        entity.setCreateId(operatorId);
        entity.setUpdateId(operatorId);
        return codeLimitService.save(entity) ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody CodeLimitVO codeLimitVO) {
        if (codeLimitVO == null || codeLimitVO.getId() == null) {
            return Result.error("id is required");
        }
        if (!isValidForSave(codeLimitVO)) {
            return Result.error("limitTime, limitCount and description are required");
        }
        CodeLimit entity = toEntity(codeLimitVO);
        entity.setUpdateId(OperatorContextUtils.currentOperatorId());
        return codeLimitService.update(entity) ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO<?> del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        return codeLimitService.deleteBatch(ids, OperatorContextUtils.currentOperatorId())
                ? Result.ok("delete success")
                : Result.error("delete failed");
    }

    private boolean isValidForSave(CodeLimitVO vo) {
        return vo != null
                && vo.getLimitTime() != null
                && vo.getLimitCount() != null
                && StringUtils.hasText(vo.getDespcription());
    }

    private CodeLimit toEntity(CodeLimitVO vo) {
        CodeLimit entity = new CodeLimit();
        if (vo == null) {
            return entity;
        }
        entity.setId(vo.getId());
        entity.setLimitTime(vo.getLimitTime());
        entity.setLimitCount(vo.getLimitCount());
        entity.setDescription(vo.getDespcription());
        entity.setExtend1(String.valueOf(vo.getLimitState() == null ? 1 : vo.getLimitState()));
        return entity;
    }

    private CodeLimitVO toVO(CodeLimit entity) {
        CodeLimitVO vo = new CodeLimitVO();
        if (entity == null) {
            return vo;
        }
        vo.setId(entity.getId());
        vo.setLimitTime(entity.getLimitTime());
        vo.setLimitCount(entity.getLimitCount());
        vo.setDespcription(entity.getDescription());
        vo.setLimitState(parseLimitState(entity.getExtend1()));
        return vo;
    }

    private Integer parseLimitState(String value) {
        if (!StringUtils.hasText(value)) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignore) {
            return 1;
        }
    }
}
