package com.cz.test.mapper;

import com.cz.test.client.CacheClient;
import com.cz.test.entity.MobileTransfer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;



@SpringBootTest
@RunWith(SpringRunner.class)
public class MobileTransferMapperTest {

    @Autowired
    private MobileTransferMapper mapper;

    @Autowired
    private CacheClient cacheClient;


    @Test
    public void findAll() {
        List<MobileTransfer> list = mapper.findAll();

        // 1. 打印查出来的列表总大小，确认第一步是不是查少了
        System.out.println("【排查】从数据库查出的条数：" + list.size());

        for (MobileTransfer mobileTransfer : list) {
            // 2. 打印一下正在处理的 Key，看看有没有 null 或者重复的
            String redisKey = "transfer:" + mobileTransfer.getTransferNumber();
            System.out.println("【排查】正在写入Redis Key: " + redisKey);

            cacheClient.set(redisKey, mobileTransfer.getNowIsp());
        }
    }
}