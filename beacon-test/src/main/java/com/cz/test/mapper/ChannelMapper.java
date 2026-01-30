package com.cz.test.mapper;

import com.cz.test.entity.Channel;
import com.cz.test.entity.ClientBalance;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChannelMapper {
    @Select("select * from channel where is_delete = 0")
    List<Channel> findAll();
}
