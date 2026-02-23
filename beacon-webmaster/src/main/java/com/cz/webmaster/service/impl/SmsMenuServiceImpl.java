package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.SmsMenu;
import com.cz.webmaster.entity.SmsMenuExample;
import com.cz.webmaster.mapper.SmsMenuMapper;
import com.cz.webmaster.service.SmsMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * @author cz
 * @description
 */
@Service
public class SmsMenuServiceImpl implements SmsMenuService {

    @Autowired
    private SmsMenuMapper menuMapper;


    @Override
    public List<Map<String, Object>> findUserMenu(Integer id) {
        List<Map<String, Object>> list = new ArrayList<>(menuMapper.findMenuByUserId(id));
        List<Map<String, Object>> data = new ArrayList<>();

        ListIterator<Map<String, Object>> parentIterator = list.listIterator();
        while (parentIterator.hasNext()) {
            Map<String, Object> menu = parentIterator.next();
            if (toInt(menu.get("type")) == 0) {
                data.add(menu);
                parentIterator.remove();
            }
        }

        for (Map<String, Object> parentMenu : data) {
            List<Map<String, Object>> sonMenuList = new ArrayList<>();
            ListIterator<Map<String, Object>> sonIterator = list.listIterator();
            while (sonIterator.hasNext()) {
                Map<String, Object> sonMenu = sonIterator.next();
                if (toLong(parentMenu.get("id")) == toLong(sonMenu.get("parentId"))) {
                    sonMenuList.add(sonMenu);
                    sonIterator.remove();
                }
            }
            parentMenu.put("list", sonMenuList);
        }
        return data;
    }

    @Override
    public List<SmsMenu> findByKeyword(String keyword) {
        SmsMenuExample example = buildExample(keyword);
        example.setOrderByClause("sort asc, id asc");
        return menuMapper.selectByExample(example);
    }

    @Override
    public long countByKeyword(String keyword) {
        SmsMenuExample example = buildExample(keyword);
        return menuMapper.countByExample(example);
    }

    @Override
    public SmsMenu findById(Integer id) {
        return menuMapper.selectByPrimaryKey(id);
    }

    @Override
    public List<SmsMenu> findAll() {
        SmsMenuExample example = new SmsMenuExample();
        SmsMenuExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        example.setOrderByClause("sort asc, id asc");
        return menuMapper.selectByExample(example);
    }

    @Override
    public boolean save(SmsMenu menu) {
        if (menu == null) {
            return false;
        }
        Date now = new Date();
        menu.setCreated(now);
        menu.setUpdated(now);
        if (menu.getIsDelete() == null) {
            menu.setIsDelete((byte) 0);
        }
        return menuMapper.insertSelective(menu) > 0;
    }

    @Override
    public boolean update(SmsMenu menu) {
        if (menu == null || menu.getId() == null) {
            return false;
        }
        menu.setUpdated(new Date());
        return menuMapper.updateByPrimaryKeySelective(menu) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Date now = new Date();
        for (Integer id : ids) {
            SmsMenu menu = new SmsMenu();
            menu.setId(id);
            menu.setIsDelete((byte) 1);
            menu.setUpdated(now);
            if (menuMapper.updateByPrimaryKeySelective(menu) <= 0) {
                return false;
            }
        }
        return true;
    }

    private SmsMenuExample buildExample(String keyword) {
        SmsMenuExample example = new SmsMenuExample();
        SmsMenuExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        if (StringUtils.hasText(keyword)) {
            criteria.andNameLike("%" + keyword.trim() + "%");
        }
        return example;
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
