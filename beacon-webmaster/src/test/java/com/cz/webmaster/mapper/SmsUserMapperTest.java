package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.SmsUser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SmsUserMapperTest {

    @Resource
    private SmsUserMapper smsUserMapper;

    @Test
    public void findById(){
        SmsUser smsUser = smsUserMapper.selectByPrimaryKey(1);
        System.out.println(smsUser);
    }

}