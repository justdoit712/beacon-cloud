package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.controller.support.OperatorContextUtils;
import com.cz.webmaster.entity.MobileBlack;
import com.cz.webmaster.service.MobileBlackService;
import com.cz.webmaster.vo.MobileBlackVO;
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
@RequestMapping("/sys/black")
public class SysBlackController {

    private final MobileBlackService mobileBlackService;

    public SysBlackController(MobileBlackService mobileBlackService) {
        this.mobileBlackService = mobileBlackService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                                @RequestParam(defaultValue = "10") int limit,
                                @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = limit <= 0 ? 10 : limit;
        long total = mobileBlackService.countByKeyword(keyword);
        List<MobileBlack> list = mobileBlackService.findListByPage(keyword, safeOffset, safeLimit);

        List<MobileBlackVO> rows = new ArrayList<>();
        if (list != null) {
            for (MobileBlack item : list) {
                rows.add(toVO(item));
            }
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        MobileBlackVO vo = toVO(mobileBlackService.findById(id));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("msg", "");
        result.put("black", vo);
        result.put("data", vo);
        return result;
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody MobileBlackVO mobileBlackVO) {
        if (!isValidForSave(mobileBlackVO)) {
            return Result.error("mobile is required");
        }
        MobileBlack entity = toEntity(mobileBlackVO);
        Long operatorId = OperatorContextUtils.currentOperatorId();
        entity.setCreateId(operatorId);
        entity.setUpdateId(operatorId);
        return mobileBlackService.save(entity) ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody MobileBlackVO mobileBlackVO) {
        if (mobileBlackVO == null || mobileBlackVO.getId() == null) {
            return Result.error("id is required");
        }
        if (!StringUtils.hasText(mobileBlackVO.getMobile())) {
            return Result.error("mobile is required");
        }
        MobileBlack entity = toEntity(mobileBlackVO);
        entity.setUpdateId(OperatorContextUtils.currentOperatorId());
        return mobileBlackService.update(entity) ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO<?> del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        return mobileBlackService.deleteBatch(ids, OperatorContextUtils.currentOperatorId())
                ? Result.ok("delete success")
                : Result.error("delete failed");
    }

    private boolean isValidForSave(MobileBlackVO vo) {
        return vo != null && StringUtils.hasText(vo.getMobile());
    }

    private MobileBlack toEntity(MobileBlackVO vo) {
        MobileBlack entity = new MobileBlack();
        if (vo == null) {
            return entity;
        }
        entity.setId(vo.getId());
        entity.setBlackNumber(vo.getMobile());
        entity.setBlackType(vo.getOwntype());
        entity.setClientId(vo.getClientId());
        return entity;
    }

    private MobileBlackVO toVO(MobileBlack entity) {
        MobileBlackVO vo = new MobileBlackVO();
        if (entity == null) {
            return vo;
        }
        vo.setId(entity.getId());
        vo.setMobile(entity.getBlackNumber());
        vo.setOwntype(entity.getBlackType());
        vo.setClientId(entity.getClientId());
        vo.setCreater(entity.getCreateId() == null ? null : String.valueOf(entity.getCreateId()));
        return vo;
    }
}
