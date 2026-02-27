package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.converter.SysMenuConverter;
import com.cz.webmaster.dto.SysMenuForm;
import com.cz.webmaster.entity.SmsMenu;
import com.cz.webmaster.service.SmsMenuService;
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
 * 菜单管理接口
 */
@RestController
@RequestMapping("/sys/menu")
public class SysMenuController {

    private final SmsMenuService menuService;

    public SysMenuController(SmsMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/list")
    public ResultVO list(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                         @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                         @RequestParam(value = "search", required = false) String keyword) {
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;

        List<SmsMenu> menus = menuService.findByKeyword(keyword);
        long total = menuService.countByKeyword(keyword);
        Map<Integer, String> nameMap = SysMenuConverter.buildNameMap(menuService.findAll());

        int fromIndex = Math.min(safeOffset, menus.size());
        int toIndex = Math.min(safeOffset + safeLimit, menus.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (SmsMenu menu : menus.subList(fromIndex, toIndex)) {
            rows.add(SysMenuConverter.toView(menu, nameMap));
        }
        return Result.ok(total, rows);
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") Integer id) {
        SmsMenu menu = menuService.findById(id);
        Map<Integer, String> nameMap = SysMenuConverter.buildNameMap(menuService.findAll());
        return Collections.singletonMap("menu", SysMenuConverter.toView(menu, nameMap));
    }

    @GetMapping("/select")
    public Map<String, Object> select() {
        List<SmsMenu> menus = menuService.findAll();
        List<Map<String, Object>> menuList = new ArrayList<>();
        menuList.add(SysMenuConverter.rootNode());
        for (SmsMenu menu : menus) {
            menuList.add(SysMenuConverter.toTreeNode(menu));
        }
        return Collections.singletonMap("menuList", menuList);
    }

    @PostMapping("/save")
    public ResultVO save(@RequestBody SysMenuForm form) {
        if (form == null || !StringUtils.hasText(form.getName())) {
            return Result.error("菜单名称不能为空");
        }
        SmsMenu menu = SysMenuConverter.toEntity(form);
        boolean success = menuService.save(menu);
        return success ? Result.ok("新增成功") : Result.error("新增失败");
    }

    @PostMapping("/update")
    public ResultVO update(@RequestBody SysMenuForm form) {
        if (form == null || form.getId() == null) {
            return Result.error("菜单id不能为空");
        }
        SmsMenu menu = SysMenuConverter.toEntity(form);
        boolean success = menuService.update(menu);
        return success ? Result.ok("修改成功") : Result.error("修改失败");
    }

    @PostMapping("/del")
    public ResultVO delete(@RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.error("请选择要删除的数据");
        }
        boolean success = menuService.deleteBatch(ids);
        return success ? Result.ok("删除成功") : Result.error("删除失败");
    }
}

