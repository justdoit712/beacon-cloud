package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.MobileBlack;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * Data access for {@code mobile_black} snapshot queries.
 */
public interface MobileBlackMapper {

    List<MobileBlack> findListByPage(@Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    MobileBlack findById(@Param("id") Long id);

    List<MobileBlack> findByIds(@Param("ids") List<Long> ids);

    /**
     * Load all active black list rows for cache rebuild.
     *
     * @return active black list rows
     */
    List<MobileBlack> findAllActive();

    int insertSelective(MobileBlack row);

    int updateById(MobileBlack row);

    int deleteBatch(@Param("ids") List<Long> ids,
                    @Param("updated") Date updated,
                    @Param("updateId") Long updateId);
}
