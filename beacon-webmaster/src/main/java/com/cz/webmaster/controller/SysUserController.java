package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.SysUserConverter;
import com.cz.webmaster.dto.SysUserForm;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.SmsUserService;
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
import java.util.List;
import java.util.Map;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController {

    private final SmsUserService userService;

    public SysUserController(SmsUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                         @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;

        List<SmsUser> users = userService.findByKeyword(keyword);
        long total = userService.countByKeyword(keyword);

        int fromIndex = Math.min(safeOffset, users.size());
        int toIndex = Math.min(safeOffset + safeLimit, users.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SmsUser user : users.subList(fromIndex, toIndex)) {
            rows.add(SysUserConverter.toView(user, false));
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public ResultVO<Map<String, Object>> info(@PathVariable("id") Integer id) {
        SmsUser user = userService.findById(id);
        return Result.ok(SysUserConverter.toView(user, true));
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody SysUserForm form) {
        if (form == null || !StringUtils.hasText(form.getUsercode())) {
            return Result.error("用户名不能为空");
        }
        if (userService.findByUsername(form.getUsercode().trim()) != null) {
            return Result.error("用户名已存在");
        }

        SmsUser user = SysUserConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            user.setCreateId(currentUser.getId().longValue());
            user.setUpdateId(currentUser.getId().longValue());
        }

        boolean success = userService.save(user);
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody SysUserForm form) {
        if (form == null || form.getId() == null) {
            return Result.error("用户id不能为空");
        }
        if (StringUtils.hasText(form.getUsercode())) {
            SmsUser existing = userService.findByUsername(form.getUsercode().trim());
            if (existing != null && !existing.getId().equals(form.getId())) {
                return Result.error("用户名已存在");
            }
        }

        SmsUser user = SysUserConverter.toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            user.setUpdateId(currentUser.getId().longValue());
        }

        boolean success = userService.update(user);
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO<?> delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }

        boolean success = userService.deleteBatch(ids);
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }
}


