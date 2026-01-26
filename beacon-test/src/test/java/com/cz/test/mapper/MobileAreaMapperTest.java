package com.cz.test.mapper;

import com.cz.test.client.CacheClient;
import com.cz.test.entity.MobileArea;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
@SpringBootTest
@RunWith(SpringRunner.class)
public class MobileAreaMapperTest {

    @Autowired
    private MobileAreaMapper mapper;

    @Autowired
    private CacheClient cacheClient;

    @Test
    public void queryAll() {
        List<MobileArea> list = mapper.queryAll();
        Map map = new HashMap(list.size());
        for (MobileArea mobileArea : list) {
            map.put("phase:" + mobileArea.getMobileNumber(),mobileArea.getMobileArea() + "," + mobileArea.getMobileType());
        }
        cacheClient.pipeline(map);
    }
}