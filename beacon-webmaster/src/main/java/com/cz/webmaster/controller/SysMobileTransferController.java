package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.entity.MobileTransfer;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.MobileTransferService;
import com.cz.webmaster.vo.MobileTransferVO;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/mobiletransfer")
public class SysMobileTransferController {

    private final MobileTransferService mobileTransferService;

    public SysMobileTransferController(MobileTransferService mobileTransferService) {
        this.mobileTransferService = mobileTransferService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(defaultValue = "0") int offset,
                         @RequestParam(defaultValue = "10") int limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        long total = mobileTransferService.countByKeyword(keyword);
        List<MobileTransfer> list = mobileTransferService.findListByPage(keyword, offset, limit);
        List<MobileTransferVO> rows = new ArrayList<>();
        for (MobileTransfer entity : list) {
            MobileTransferVO vo = new MobileTransferVO();
            BeanUtils.copyProperties(entity, vo);
            rows.add(vo);
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        MobileTransfer entity = mobileTransferService.findById(id);
        MobileTransferVO vo = new MobileTransferVO();
        if (entity != null) {
            BeanUtils.copyProperties(entity, vo);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("mobiletransfer", vo);
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody MobileTransferVO mobileTransferVO) {
        if (!isValidForSave(mobileTransferVO)) {
            return Result.error("mobile_transfer required fields are missing");
        }
        MobileTransfer entity = new MobileTransfer();
        BeanUtils.copyProperties(mobileTransferVO, entity);

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            entity.setCreateId(currentUser.getId().longValue());
            entity.setUpdateId(currentUser.getId().longValue());
        }
        return mobileTransferService.save(entity) ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody MobileTransferVO mobileTransferVO) {
        if (mobileTransferVO == null || mobileTransferVO.getId() == null) {
            return Result.error("mobile_transfer id is required");
        }
        MobileTransfer entity = new MobileTransfer();
        BeanUtils.copyProperties(mobileTransferVO, entity);

        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            entity.setUpdateId(currentUser.getId().longValue());
        }
        return mobileTransferService.update(entity) ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO del(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids is required");
        }
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        Long updateId = currentUser == null ? null : currentUser.getId().longValue();
        return mobileTransferService.deleteBatch(ids, updateId) ? Result.ok("delete success") : Result.error("delete failed");
    }

    private boolean isValidForSave(MobileTransferVO mobileTransferVO) {
        return mobileTransferVO != null
                && StringUtils.hasText(mobileTransferVO.getTransferNumber())
                && StringUtils.hasText(mobileTransferVO.getAreaCode())
                && mobileTransferVO.getInitIsp() != null
                && mobileTransferVO.getNowIsp() != null
                && mobileTransferVO.getIsTransfer() != null;
    }
}

