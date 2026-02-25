package com.cz.webmaster.controller;

import com.cz.webmaster.dto.SysRoleForm;
import com.cz.webmaster.entity.SmsMenu;
import com.cz.webmaster.entity.SmsRole;
import com.cz.webmaster.service.SmsMenuService;
import com.cz.webmaster.service.SmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/sys/role")
public class SystemRoleController {

    @Autowired
    private SmsRoleService roleService;

    @Autowired
    private SmsMenuService menuService;

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam Map<String, Object> params) {
        int offset = parseInt(params.get("offset"), 0);
        int limit = parseInt(params.get("limit"), 10);
        String name = toStr(params.get("name"));
        String status = toStr(params.get("status"));

        List<SmsRole> roles = roleService.findByCondition(name, status);
        long total = roleService.countByCondition(name, status);

        int fromIndex = Math.min(offset, roles.size());
        int toIndex = Math.min(offset + limit, roles.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SmsRole role : roles.subList(fromIndex, toIndex)) {
            rows.add(toView(role));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        return result;
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Integer id) {
        SmsRole role = roleService.findById(id);
        Map<String, Object> result = new HashMap<>();
        result.put("role", toView(role));
        return result;
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody SysRoleForm form) {
        if (form == null || !StringUtils.hasText(form.getName())) {
            return fail("角色名称不能为空");
        }
        if (roleService.existsByName(form.getName(), null)) {
            return fail("角色名称已存在");
        }
        SmsRole role = toEntity(form);
        boolean success = roleService.save(role);
        return success ? ok("新增成功") : fail("新增失败");
    }

    @PostMapping("/update")
    public Map<String, Object> update(@RequestBody SysRoleForm form) {
        if (form == null || form.getId() == null) {
            return fail("角色id不能为空");
        }
        if (StringUtils.hasText(form.getName()) && roleService.existsByName(form.getName(), form.getId())) {
            return fail("角色名称已存在");
        }
        SmsRole role = toEntity(form);
        boolean success = roleService.update(role);
        return success ? ok("修改成功") : fail("修改失败");
    }

    @PostMapping("/del")
    public Map<String, Object> delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return fail("请选择要删除的数据");
        }
        boolean success = roleService.deleteBatch(ids);
        return success ? ok("删除成功") : fail("删除失败");
    }

    @GetMapping("/menu/tree")
    public Map<String, Object> menuTree() {
        List<SmsMenu> menus = menuService.findAll();
        List<Map<String, Object>> menuList = new ArrayList<>();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", 0);
        root.put("parentId", -1L);
        root.put("name", "一级菜单");
        menuList.add(root);

        for (SmsMenu menu : menus) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", menu.getId());
            node.put("parentId", menu.getParentId());
            node.put("name", menu.getName());
            menuList.add(node);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("menuList", menuList);
        return result;
    }

    @GetMapping("/menu/{roleId}")
    public List<Integer> roleMenu(@PathVariable("roleId") Integer roleId) {
        return roleService.findMenuIdsByRoleId(roleId);
    }

    @PostMapping("/menu/assign")
    public Map<String, Object> assignMenu(@RequestParam("roleId") Integer roleId,
                                          @RequestParam(value = "menuIds", required = false) List<Integer> menuIds) {
        boolean success = roleService.assignMenu(roleId, menuIds);
        return success ? ok("分配成功") : fail("分配失败");
    }

    private SmsRole toEntity(SysRoleForm form) {
        SmsRole role = new SmsRole();
        role.setId(form.getId());
        role.setName(form.getName() == null ? null : form.getName().trim());
        role.setExtend1(form.getRemark());
        if (form.getStatus() != null) {
            role.setExtend2(String.valueOf(form.getStatus()));
        }
        return role;
    }

    private Map<String, Object> toView(SmsRole role) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (role == null) {
            return data;
        }
        data.put("id", role.getId());
        data.put("name", role.getName());
        data.put("remark", role.getExtend1());
        data.put("status", parseInt(role.getExtend2(), 1));
        return data;
    }

    private Map<String, Object> ok(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", true);
        result.put("msg", msg);
        result.put("message", msg);
        return result;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", false);
        result.put("msg", msg);
        result.put("message", msg);
        return result;
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
