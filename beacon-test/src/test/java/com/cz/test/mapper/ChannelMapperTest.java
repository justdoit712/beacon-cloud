package com.cz.test.mapper;

import com.cz.test.client.CacheClient;
import com.cz.test.entity.Channel;
import com.cz.test.entity.ClientBalance;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ChannelMapperTest {

    @Autowired
    private ChannelMapper mapper;

    @Autowired
    private CacheClient cacheClient;

    @Test
    public void findAll() throws JsonProcessingException {
        List<Channel> channels = mapper.findAll();

        for (Channel channel : channels) {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(mapper.writeValueAsString(channel), Map.class);
            cacheClient.hmset("channel:" + channel.getId(), map);
        }
    }
}