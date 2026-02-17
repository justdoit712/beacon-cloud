package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.ClientBusinessExample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ClientBusinessMapper {
    long countByExample(ClientBusinessExample example);

    int deleteByExample(ClientBusinessExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ClientBusiness row);

    int insertSelective(ClientBusiness row);

    List<ClientBusiness> selectByExample(ClientBusinessExample example);

    ClientBusiness selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ClientBusiness row, @Param("example") ClientBusinessExample example);

    int updateByExample(@Param("row") ClientBusiness row, @Param("example") ClientBusinessExample example);

    int updateByPrimaryKeySelective(ClientBusiness row);

    int updateByPrimaryKey(ClientBusiness row);
}