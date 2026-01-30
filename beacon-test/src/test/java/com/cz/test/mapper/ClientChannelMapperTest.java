package com.cz.test.mapper;

import com.cz.test.client.CacheClient;
import com.cz.test.entity.Channel;
import com.cz.test.entity.ClientChannel;
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
public class ClientChannelMapperTest {

    @Autowired
    private ClientChannelMapper mapper;

    @Autowired
    private CacheClient cacheClient;

    @Test
    public void findAll() throws JsonProcessingException {
        List<ClientChannel> channels = mapper.findAll();

        for (ClientChannel ClientChannel : channels) {
            ObjectMapper mapper = new ObjectMapper();
            Map map = mapper.readValue(mapper.writeValueAsString(ClientChannel), Map.class);
            cacheClient.sadd("client_channel:" + ClientChannel.getClientId(), map);
        }
    }
}