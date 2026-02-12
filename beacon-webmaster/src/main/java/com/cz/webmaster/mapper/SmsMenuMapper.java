package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.SmsMenu;
import com.cz.webmaster.entity.SmsMenuExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsMenuMapper {
    long countByExample(SmsMenuExample example);

    int deleteByExample(SmsMenuExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(SmsMenu row);

    int insertSelective(SmsMenu row);

    List<SmsMenu> selectByExample(SmsMenuExample example);

    SmsMenu selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("row") SmsMenu row, @Param("example") SmsMenuExample example);

    int updateByExample(@Param("row") SmsMenu row, @Param("example") SmsMenuExample example);

    int updateByPrimaryKeySelective(SmsMenu row);

    int updateByPrimaryKey(SmsMenu row);
}