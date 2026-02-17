package com.cz.webmaster.mapper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Set;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SmsRoleMapperTest {

    @Autowired
    private SmsRoleMapper smsRoleMapper;
    @Test
    public void findRoleNameByUserId() {
        Set<String> set =  smsRoleMapper.findRoleNameByUserId(1);
        System.out.println(set);
    }
}