package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.MobileDirtyWord;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * Data access for {@code mobile_dirtyword} snapshot queries.
 */
public interface MobileDirtyWordMapper {

    List<MobileDirtyWord> findListByPage(@Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    MobileDirtyWord findById(@Param("id") Long id);

    List<MobileDirtyWord> findByIds(@Param("ids") List<Long> ids);

    /**
     * Load all active dirty words for cache rebuild.
     *
     * @return active dirty words
     */
    List<MobileDirtyWord> findAllActive();

    int insertSelective(MobileDirtyWord row);

    int updateById(MobileDirtyWord row);

    int deleteBatch(@Param("ids") List<Long> ids,
                    @Param("updated") Date updated,
                    @Param("updateId") Long updateId);
}
