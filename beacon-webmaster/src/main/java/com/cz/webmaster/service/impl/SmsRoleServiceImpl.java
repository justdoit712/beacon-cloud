package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.SmsRole;
import com.cz.webmaster.entity.SmsRoleExample;
import com.cz.webmaster.mapper.SmsRoleMapper;
import com.cz.webmaster.service.SmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author cz
 * @description
 */
@Service
public class SmsRoleServiceImpl implements SmsRoleService {

    @Autowired
    private SmsRoleMapper roleMapper;

    @Override
    public Set<String> getRoleName(Integer userId) {
        return roleMapper.findRoleNameByUserId(userId);
    }

    @Override
    public List<SmsRole> findByCondition(String name, String status) {
        SmsRoleExample example = buildExample(name, status);
        example.setOrderByClause("id desc");
        return roleMapper.selectByExample(example);
    }

    @Override
    public long countByCondition(String name, String status) {
        SmsRoleExample example = buildExample(name, status);
        return roleMapper.countByExample(example);
    }

    @Override
    public SmsRole findById(Integer id) {
        return roleMapper.selectByPrimaryKey(id);
    }

    @Override
    public boolean save(SmsRole role) {
        if (role == null) {
            return false;
        }
        Date now = new Date();
        role.setCreated(now);
        role.setUpdated(now);
        if (role.getIsDelete() == null) {
            role.setIsDelete((byte) 0);
        }
        return roleMapper.insertSelective(role) > 0;
    }

    @Override
    public boolean update(SmsRole role) {
        if (role == null || role.getId() == null) {
            return false;
        }
        role.setUpdated(new Date());
        return roleMapper.updateByPrimaryKeySelective(role) > 0;
    }

    @Override
    public boolean existsByName(String name, Integer excludeId) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        SmsRoleExample example = new SmsRoleExample();
        SmsRoleExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        criteria.andNameEqualTo(name.trim());
        if (excludeId != null) {
            criteria.andIdNotEqualTo(excludeId);
        }
        return roleMapper.countByExample(example) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Date now = new Date();
        for (Integer id : ids) {
            SmsRole role = new SmsRole();
            role.setId(id);
            role.setIsDelete((byte) 1);
            role.setUpdated(now);
            if (roleMapper.updateByPrimaryKeySelective(role) <= 0) {
                return false;
            }
            roleMapper.deleteRoleMenuByRoleId(id);
        }
        return true;
    }

    @Override
    public List<Integer> findMenuIdsByRoleId(Integer roleId) {
        return roleMapper.findMenuIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignMenu(Integer roleId, List<Integer> menuIds) {
        if (roleId == null) {
            return false;
        }
        roleMapper.deleteRoleMenuByRoleId(roleId);
        if (menuIds == null || menuIds.isEmpty()) {
            return true;
        }
        List<Integer> cleanedMenuIds = new ArrayList<>();
        for (Integer menuId : menuIds) {
            if (menuId != null) {
                cleanedMenuIds.add(menuId);
            }
        }
        if (cleanedMenuIds.isEmpty()) {
            return true;
        }
        return roleMapper.insertRoleMenus(roleId, cleanedMenuIds) > 0;
    }

    private SmsRoleExample buildExample(String name, String status) {
        SmsRoleExample example = new SmsRoleExample();
        SmsRoleExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        if (StringUtils.hasText(name)) {
            criteria.andNameLike("%" + name.trim() + "%");
        }
        if (StringUtils.hasText(status)) {
            criteria.andExtend2EqualTo(status.trim());
        }
        return example;
    }
}
