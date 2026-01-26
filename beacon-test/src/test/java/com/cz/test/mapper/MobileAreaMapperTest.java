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
        System.out.println("数据总条数: " + list.size());

        // 定义每批次的大小
        int batchSize = 3000;
        int total = list.size();


        for (int i = 0; i < total; i += batchSize) {
            // 计算当前批次的结束索引（注意不要越界，最后一次取总长度）
            int toIndex = Math.min(i + batchSize, total);

            // subList(from, to) 包含 from，不包含 to
            List<MobileArea> batchList = list.subList(i, toIndex);

            // 4. 处理这一批数据
            Map<String, String> map = new HashMap<>(batchList.size());
            for (MobileArea mobileArea : batchList) {
                map.put("phase:" + mobileArea.getMobileNumber(),
                        mobileArea.getMobileArea() + "," + mobileArea.getMobileType());
            }

            try {
                // 发送这一批
                cacheClient.pipeline(map);
                System.out.println("成功处理批次: " + i + " - " + toIndex);
            } catch (Exception e) {
                System.err.println("批次处理失败 [" + i + "," + toIndex + "]: " + e.getMessage());
            }
        }
    }
}