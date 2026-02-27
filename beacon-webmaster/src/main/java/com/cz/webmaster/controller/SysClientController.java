package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.SysClientConverter;
import com.cz.webmaster.dto.SysClientForm;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import org.apache.shiro.SecurityUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 客户信息管理接口
 */
@RestController
@RequestMapping("/sys/client")
public class SysClientController {

    private final ClientBusinessService clientBusinessService;

    public SysClientController(ClientBusinessService clientBusinessService) {
        this.clientBusinessService = clientBusinessService;
    }

    @GetMapping("/list")
    public ResultVO list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                         @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;

        List<ClientBusiness> list = clientBusinessService.findByKeyword(keyword);
        long total = clientBusinessService.countByKeyword(keyword);

        int fromIndex = Math.min(safeOffset, list.size());
        int toIndex = Math.min(safeOffset + safeLimit, list.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClientBusiness cb : list.subList(fromIndex, toIndex)) {
            rows.add(SysClientConverter.toView(cb));
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Long id) {
        ClientBusiness cb = clientBusinessService.findById(id);
        return Collections.singletonMap("client", SysClientConverter.toView(cb));
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody SysClientForm form) {
        if (form == null || !StringUtils.hasText(form.getCorpname())) {
            return Result.error("公司名称不能为空");
        }

        ClientBusiness cb = SysClientConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setCreateId(currentUser.getId().longValue());
            cb.setUpdateId(currentUser.getId().longValue());
        }

        boolean success = clientBusinessService.save(cb);
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody SysClientForm form) {
        if (form == null || form.getId() == null) {
            return Result.error("客户id不能为空");
        }

        ClientBusiness cb = SysClientConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            cb.setUpdateId(currentUser.getId().longValue());
        }

        boolean success = clientBusinessService.update(cb);
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }

        boolean success = clientBusinessService.deleteBatch(ids);
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }
}

