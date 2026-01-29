package com.cz.test.mapper;

import com.cz.test.client.CacheClient;
import com.cz.test.entity.MobileBlack;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@RunWith(SpringRunner.class)
class MobileBlackMapperTest {

    @Autowired
    private MobileBlackMapper mobileBlackMapper;
    @Autowired
    private CacheClient cacheClient;
    @Test
    public void findAll() {
        List<MobileBlack> mobileBlackList = mobileBlackMapper.findAll();
        for (MobileBlack mobileBlack : mobileBlackList) {
            if(mobileBlack.getClientId() == 0){
                // 平台级别的黑名单   black:手机号   作为key
                cacheClient.set("black:" + mobileBlack.getBlackNumber(),"1");
            }else{
                // 客户级别的黑名单   black:clientId:手机号
                cacheClient.set("black:" + mobileBlack.getClientId() + ":" +mobileBlack.getBlackNumber(),"1");
            }
        }
    }

}