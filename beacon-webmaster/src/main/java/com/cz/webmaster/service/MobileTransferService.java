package com.cz.webmaster.service;

import com.cz.webmaster.entity.MobileTransfer;

import java.util.List;

public interface MobileTransferService {

    List<MobileTransfer> findListByPage(String keyword, int offset, int limit);

    long countByKeyword(String keyword);

    MobileTransfer findById(Long id);

    List<MobileTransfer> findAllActive();

    boolean save(MobileTransfer mobileTransfer);

    boolean update(MobileTransfer mobileTransfer);

    boolean deleteBatch(List<Long> ids, Long updateId);
}
