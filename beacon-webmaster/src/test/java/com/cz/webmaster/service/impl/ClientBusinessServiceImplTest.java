package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.service.ClientBusinessService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ClientBusinessServiceImplTest {

    @Autowired
    private ClientBusinessService service;
    @Test
    public void findAll() {
        List<ClientBusiness> all = service.findAll();
        System.out.println(all);
    }

    @Test
    public void findByUserId() {
        List<ClientBusiness> list = service.findByUserId(2);
        System.out.println(list);
    }
}