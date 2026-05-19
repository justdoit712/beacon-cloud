package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.CodeLimit;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface CodeLimitMapper {

    List<CodeLimit> findListByPage(@Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    CodeLimit findById(@Param("id") Long id);

    List<CodeLimit> findByIds(@Param("ids") List<Long> ids);

    int insertSelective(CodeLimit row);

    int updateById(CodeLimit row);

    int deleteBatch(@Param("ids") List<Long> ids,
                    @Param("updated") Date updated,
                    @Param("updateId") Long updateId);
}
