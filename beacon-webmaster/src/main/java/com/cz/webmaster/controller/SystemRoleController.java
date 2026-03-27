package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.SysRoleConverter;
import com.cz.webmaster.dto.SysRoleForm;
import com.cz.webmaster.entity.SmsMenu;
import com.cz.webmaster.entity.SmsRole;
import com.cz.webmaster.service.SmsMenuService;
import com.cz.webmaster.service.SmsRoleService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/role")
public class SystemRoleController {

    private final SmsRoleService roleService;
    private final SmsMenuService menuService;

    public SystemRoleController(SmsRoleService roleService, SmsMenuService menuService) {
        this.roleService = roleService;
        this.menuService = menuService;
    }

    @GetMapping("/list")
    public PageResultVO<?> list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                                @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                                @RequestParam(value = "name", required = false) String name,
                                @RequestParam(value = "status", required = false) String status) {
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;

        List<SmsRole> roles = roleService.findByCondition(name, status);
        long total = roleService.countByCondition(name, status);

        int fromIndex = Math.min(safeOffset, roles.size());
        int toIndex = Math.min(safeOffset + safeLimit, roles.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SmsRole role : roles.subList(fromIndex, toIndex)) {
            rows.add(SysRoleConverter.toView(role));
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public ResultVO<?> info(@PathVariable("id") Integer id) {
        SmsRole role = roleService.findById(id);
        return Result.ok(Collections.singletonMap("role", SysRoleConverter.toView(role)));
    }

    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody SysRoleForm form) {
        if (form == null || !StringUtils.hasText(form.getName())) {
            return Result.error("role name is required");
        }
        if (roleService.existsByName(form.getName(), null)) {
            return Result.error("role name already exists");
        }

        SmsRole role = SysRoleConverter.toEntity(form);
        boolean success = roleService.save(role);
        return success ? Result.ok("save success") : Result.error("save failed");
    }

    @PostMapping("/update")
    public ResultVO<?> update(@RequestBody SysRoleForm form) {
        if (form == null || form.getId() == null) {
            return Result.error("role id is required");
        }
        if (StringUtils.hasText(form.getName()) && roleService.existsByName(form.getName(), form.getId())) {
            return Result.error("role name already exists");
        }

        SmsRole role = SysRoleConverter.toEntity(form);
        boolean success = roleService.update(role);
        return success ? Result.ok("update success") : Result.error("update failed");
    }

    @PostMapping("/del")
    public ResultVO<?> delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("ids are required");
        }
        boolean success = roleService.deleteBatch(ids);
        return success ? Result.ok("delete success") : Result.error("delete failed");
    }

    @GetMapping("/menu/tree")
    public ResultVO<?> menuTree() {
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

        return Result.ok(Collections.singletonMap("menuList", menuList));
    }

    @GetMapping("/menu/{roleId}")
    public ResultVO<?> roleMenu(@PathVariable("roleId") Integer roleId) {
        return Result.ok(roleService.findMenuIdsByRoleId(roleId));
    }

    @PostMapping("/menu/assign")
    public ResultVO<?> assignMenu(@RequestParam("roleId") Integer roleId,
                                  @RequestParam(value = "menuIds", required = false) List<Integer> menuIds) {
        boolean success = roleService.assignMenu(roleId, menuIds);
        return success ? Result.ok("assign success") : Result.error("assign failed");
    }
}
