package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.SmsUserService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SmsUserServiceImplTest {

    @Autowired
    private SmsUserService smsUserService;
    @Test
    public void findByUsername() {
        SmsUser admin = smsUserService.findByUsername("admin");
        System.out.println(admin);
    }
}