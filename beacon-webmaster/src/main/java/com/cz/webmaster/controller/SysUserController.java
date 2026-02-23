package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.SysUserForm;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.SmsUserService;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理接口
 */
@RestController
@RequestMapping("/sys/user")
public class SysUserController {

    @Autowired
    private SmsUserService userService;

    @GetMapping("/list")
    public ResultVO list(@RequestParam Map<String, Object> params) {
        int offset = parseInt(params.get("offset"), 0);
        int limit = parseInt(params.get("limit"), 10);
        String keyword = toStr(params.get("search"));

        List<SmsUser> users = userService.findByKeyword(keyword);
        long total = userService.countByKeyword(keyword);

        int fromIndex = Math.min(offset, users.size());
        int toIndex = Math.min(offset + limit, users.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SmsUser user : users.subList(fromIndex, toIndex)) {
            rows.add(toView(user, false));
        }
        return R.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Integer id) {
        SmsUser user = userService.findById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("user", toView(user, true));
        return result;
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody SysUserForm form) {
        if (form == null || !StringUtils.hasText(form.getUsercode())) {
            return error("用户名不能为空");
        }
        SmsUser user = toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            user.setCreateId(currentUser.getId().longValue());
            user.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = userService.save(user);
        return success ? success("新增成功") : error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody SysUserForm form) {
        if (form == null || form.getId() == null) {
            return error("用户id不能为空");
        }
        SmsUser user = toEntity(form);
        SmsUser currentUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (currentUser != null) {
            user.setUpdateId(currentUser.getId().longValue());
        }
        boolean success = userService.update(user);
        return success ? success("修改成功") : error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return error("请选择要删除的数据");
        }
        boolean success = userService.deleteBatch(ids);
        return success ? success("删除成功") : error("删除失败");
    }

    private SmsUser toEntity(SysUserForm form) {
        SmsUser user = new SmsUser();
        user.setId(form.getId());
        user.setUsername(form.getUsercode());
        user.setPassword(form.getPassword());
        user.setNickname(form.getRealName());
        user.setExtend1(form.getEmail());
        if (form.getType() != null) {
            user.setExtend2(String.valueOf(form.getType()));
        }
        if (form.getStatus() != null) {
            user.setExtend3(String.valueOf(form.getStatus()));
        }
        if (form.getClientid() != null) {
            user.setExtend4(String.valueOf(form.getClientid()));
        }
        return user;
    }

    private Map<String, Object> toView(SmsUser user, boolean forEdit) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (user == null) {
            return data;
        }
        data.put("id", user.getId());
        data.put("usercode", user.getUsername());
        data.put("password", forEdit ? "" : user.getPassword());
        data.put("email", user.getExtend1());
        data.put("realName", user.getNickname());
        data.put("type", parseInt(user.getExtend2(), 2));
        data.put("status", parseInt(user.getExtend3(), 1));
        data.put("clientid", user.getExtend4());
        return data;
    }

    private ResultVO success(String msg) {
        ResultVO resultVO = R.ok();
        resultVO.setMsg(msg);
        return resultVO;
    }

    private ResultVO error(String msg) {
        return new ResultVO(-1, msg);
    }

    private String toStr(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
