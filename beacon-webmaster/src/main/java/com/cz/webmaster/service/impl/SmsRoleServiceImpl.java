package com.cz.webmaster.service.impl;

import com.cz.webmaster.mapper.SmsRoleMapper;
import com.cz.webmaster.service.SmsRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        Set<String> roleNameSet = roleMapper.findRoleNameByUserId(userId);
        return roleNameSet;
    }
}
