package com.cz.webmaster.controller;

import com.cz.common.util.R;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.SysMenuForm;
import com.cz.webmaster.entity.SmsMenu;
import com.cz.webmaster.service.SmsMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单管理接口
 */
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController {

    @Autowired
    private SmsMenuService menuService;

    @GetMapping("/list")
    public ResultVO list(@RequestParam Map<String, Object> params) {
        int offset = parseInt(params.get("offset"), 0);
        int limit = parseInt(params.get("limit"), 10);
        String keyword = toStr(params.get("search"));

        List<SmsMenu> menus = menuService.findByKeyword(keyword);
        long total = menuService.countByKeyword(keyword);

        Map<Integer, String> nameMap = buildNameMap(menuService.findAll());

        int fromIndex = Math.min(offset, menus.size());
        int toIndex = Math.min(offset + limit, menus.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SmsMenu menu : menus.subList(fromIndex, toIndex)) {
            rows.add(toView(menu, nameMap));
        }
        return R.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Integer id) {
        SmsMenu menu = menuService.findById(id);
        Map<Integer, String> nameMap = buildNameMap(menuService.findAll());
        Map<String, Object> result = new HashMap<>();
        result.put("menu", toView(menu, nameMap));
        return result;
    }

    @GetMapping("/select")
    public Map<String, Object> select() {
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

    @PostMapping("/save")
    public ResultVO save(@RequestBody SysMenuForm form) {
        if (form == null || !StringUtils.hasText(form.getName())) {
            return error("菜单名称不能为空");
        }
        SmsMenu menu = toEntity(form);
        boolean success = menuService.save(menu);
        return success ? success("新增成功") : error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody SysMenuForm form) {
        if (form == null || form.getId() == null) {
            return error("菜单id不能为空");
        }
        SmsMenu menu = toEntity(form);
        boolean success = menuService.update(menu);
        return success ? success("修改成功") : error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return error("请选择要删除的数据");
        }
        boolean success = menuService.deleteBatch(ids);
        return success ? success("删除成功") : error("删除失败");
    }

    private SmsMenu toEntity(SysMenuForm form) {
        SmsMenu menu = new SmsMenu();
        menu.setId(form.getId());
        menu.setName(form.getName());
        menu.setParentId(form.getParentId() == null ? 0L : form.getParentId());
        menu.setUrl(form.getUrl());
        menu.setIcon(form.getIcon());
        menu.setType(form.getType());
        menu.setSort(form.getOrderNum() == null ? 0 : form.getOrderNum());
        menu.setExtend1(form.getPerms());
        return menu;
    }

    private Map<String, Object> toView(SmsMenu menu, Map<Integer, String> nameMap) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (menu == null) {
            return data;
        }
        data.put("id", menu.getId());
        data.put("name", menu.getName());
        data.put("parentId", menu.getParentId());
        data.put("parentName", nameMap.get(toInt(menu.getParentId())));
        data.put("url", menu.getUrl());
        data.put("icon", menu.getIcon());
        data.put("type", menu.getType());
        data.put("perms", menu.getExtend1());
        data.put("orderNum", menu.getSort());
        return data;
    }

    private Map<Integer, String> buildNameMap(List<SmsMenu> menus) {
        Map<Integer, String> nameMap = new HashMap<>();
        nameMap.put(0, "一级菜单");
        for (SmsMenu menu : menus) {
            nameMap.put(menu.getId(), menu.getName());
        }
        return nameMap;
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

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return parseInt(value, 0);
    }
}
