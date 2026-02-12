package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.entity.SmsUserExample;
import com.cz.webmaster.mapper.SmsUserMapper;
import com.cz.webmaster.service.SmsUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author cz
 * @description
 */
@Service
public class SmsUserServiceImpl implements SmsUserService {

    @Autowired
    private SmsUserMapper userMapper;

    @Override
    public SmsUser findByUsername(String username) {
        //1、封装查询条件
        SmsUserExample example = new SmsUserExample();
        SmsUserExample.Criteria criteria = example.createCriteria();
        criteria.andUsernameEqualTo(username);
        //2、基于userMapper查询
        List<SmsUser> list = userMapper.selectByExample(example);
        //3、返回
        return list != null ? list.get(0) : null;
    }
}
