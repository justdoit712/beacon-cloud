package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.Channel;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface ChannelMapper {

    List<Channel> findListByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    Channel findById(@Param("id") Long id);

    List<Channel> findAllActive();

    int insertSelective(Channel channel);

    int updateById(Channel channel);

    int deleteBatch(@Param("ids") List<Long> ids, @Param("updated") Date updated, @Param("updateId") Long updateId);


}



