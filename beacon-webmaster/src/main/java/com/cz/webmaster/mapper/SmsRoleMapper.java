package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.SmsRole;
import com.cz.webmaster.entity.SmsRoleExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SmsRoleMapper {
    long countByExample(SmsRoleExample example);

    int deleteByExample(SmsRoleExample example);

    int deleteByPrimaryKey(Integer id);

    int insert(SmsRole row);

    int insertSelective(SmsRole row);

    List<SmsRole> selectByExample(SmsRoleExample example);

    SmsRole selectByPrimaryKey(Integer id);

    int updateByExampleSelective(@Param("row") SmsRole row, @Param("example") SmsRoleExample example);

    int updateByExample(@Param("row") SmsRole row, @Param("example") SmsRoleExample example);

    int updateByPrimaryKeySelective(SmsRole row);

    int updateByPrimaryKey(SmsRole row);
}