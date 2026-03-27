package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.MobileTransfer;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface MobileTransferMapper {

    List<MobileTransfer> findListByPage(@Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countByKeyword(@Param("keyword") String keyword);

    MobileTransfer findById(@Param("id") Long id);

    List<MobileTransfer> findByIds(@Param("ids") List<Long> ids);

    List<MobileTransfer> findAllActive();

    int insertSelective(MobileTransfer mobileTransfer);

    int updateById(MobileTransfer mobileTransfer);

    int deleteBatch(@Param("ids") List<Long> ids,
                    @Param("updated") Date updated,
                    @Param("updateId") Long updateId);
}
