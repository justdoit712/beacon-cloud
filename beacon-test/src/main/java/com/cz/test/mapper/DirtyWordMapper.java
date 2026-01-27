package com.cz.test.mapper;

import com.cz.test.entity.MobileArea;
import org.apache.ibatis.annotations.Select;

import java.util.List;


public interface DirtyWordMapper {
    @Select("select dirtyword from mobile_dirtyword")
    List<String> findDirtyWord();
}
