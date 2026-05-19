package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.MobileArea;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface MobileAreaMapper {

    List<MobileArea> findListByPage(@Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    MobileArea findById(@Param("id") Long id);

    List<MobileArea> findByIds(@Param("ids") List<Long> ids);

    List<Map<String, Object>> allProvinces();

    List<Map<String, Object>> allCities(@Param("provinceCode") String provinceCode);

    MobileArea findCitySample(@Param("provinceCode") String provinceCode,
                              @Param("mobileArea") String mobileArea);

    int insertSelective(MobileArea row);

    int updateById(MobileArea row);

    int deleteBatch(@Param("ids") List<Long> ids,
                    @Param("updated") Date updated,
                    @Param("updateId") Long updateId);
}
