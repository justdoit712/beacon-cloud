package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.entity.MobileDirtyWord;
import com.cz.webmaster.service.MobileDirtyWordService;
import com.cz.webmaster.vo.MobileDirtyWordVO;
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
@RequestMapping("/sys/message")
public class SysDirtyWordController {

    private final MobileDirtyWordService mobileDirtyWordService;

    public SysDirtyWordController(MobileDirtyWordService mobileDirtyWordService) {
        this.mobileDirtyWordService = mobileDirtyWordService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "10") int limit,
                                @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = limit <= 0 ? 10 : limit;
        long total = mobileDirtyWordService.countByKeyword(keyword);
        List<MobileDirtyWord> list = mobileDirtyWordService.findListByPage(keyword, safeOffset, safeLimit);

        List<MobileDirtyWordVO> rows = new ArrayList<>();
        if (list != null) {
            for (MobileDirtyWord item : list) {
                rows.add(toVO(item));
            }
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        MobileDirtyWordVO vo = toVO(mobileDirtyWordService.findById(id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("message", vo);
        result.put("data", vo);
        return result;
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody MobileDirtyWordVO mobileDirtyWordVO) {
        if (!isValidForSave(mobileDirtyWordVO)) {
            return Result.error("dirtyword is required");
        }
        MobileDirtyWord entity = toEntity(mobileDirtyWordVO);
        Long operatorId = OperatorContextUtils.currentOperatorId();
        entity.setCreateId(operatorId);
        entity.setUpdateId(operatorId);
        return mobileDirtyWordService.save(entity) ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody MobileDirtyWordVO mobileDirtyWordVO) {
        if (mobileDirtyWordVO == null || mobileDirtyWordVO.getId() == null) {
            return Result.error("id is required");
        }
        if (!StringUtils.hasText(mobileDirtyWordVO.getDirtyword())) {
            return Result.error("dirtyword is required");
        }
        MobileDirtyWord entity = toEntity(mobileDirtyWordVO);
        entity.setUpdateId(OperatorContextUtils.currentOperatorId());
        return mobileDirtyWordService.update(entity) ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO<?> del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        return mobileDirtyWordService.deleteBatch(ids, OperatorContextUtils.currentOperatorId())
                ? Result.ok("delete success")
                : Result.error("delete failed");
    }

    private boolean isValidForSave(MobileDirtyWordVO vo) {
        return vo != null && StringUtils.hasText(vo.getDirtyword());
    }

    private MobileDirtyWord toEntity(MobileDirtyWordVO vo) {
        MobileDirtyWord entity = new MobileDirtyWord();
        if (vo == null) {
            return entity;
        }
        entity.setId(vo.getId());
        entity.setDirtyword(vo.getDirtyword());
        return entity;
    }

    private MobileDirtyWordVO toVO(MobileDirtyWord entity) {
        MobileDirtyWordVO vo = new MobileDirtyWordVO();
        if (entity == null) {
            return vo;
        }
        vo.setId(entity.getId());
        vo.setDirtyword(entity.getDirtyword());
        vo.setOwntype(1);
        vo.setCreater(entity.getCreateId() == null ? null : String.valueOf(entity.getCreateId()));
        return vo;
    }
}
