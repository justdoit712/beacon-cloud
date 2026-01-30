package com.cz.test.mapper;

import com.cz.test.entity.Channel;
import com.cz.test.entity.ClientChannel;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ClientChannelMapper {
    @Select("select * from client_channel where is_delete = 0")
    List<ClientChannel> findAll();
}
