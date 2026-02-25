package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ClientChannel;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface ClientChannelMapper {

    List<ClientChannel> findListByPage(@Param("keyword") String keyword, @Param("offset") int offset, @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    ClientChannel findById(@Param("id") Long id);

    int insertSelective(ClientChannel clientChannel);

    int updateById(ClientChannel clientChannel);

    int deleteBatch(@Param("ids") List<Long> ids, @Param("updated") Date updated, @Param("updateId") Long updateId);
}
