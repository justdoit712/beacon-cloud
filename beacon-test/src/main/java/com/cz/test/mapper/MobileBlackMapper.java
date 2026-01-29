package com.cz.test.mapper;

import com.cz.test.entity.ClientBalance;
import com.cz.test.entity.MobileBlack;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


public interface MobileBlackMapper {
    @Select("select black_number , client_id  from mobile_black where is_delete = 0")
    List<MobileBlack> findAll();
}
