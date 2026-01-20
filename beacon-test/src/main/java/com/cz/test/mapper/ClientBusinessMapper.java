package com.cz.test.mapper;

import com.cz.test.entity.ClientBusiness;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ClientBusinessMapper {
    @Select("select * from client_business where id = #{id}")
    ClientBusiness findById(@Param("id") Long id);

}
