package com.cz.webmaster.service;

import com.cz.webmaster.entity.MobileBlack;

import java.util.List;

public interface MobileBlackService {

    List<MobileBlack> findListByPage(String keyword, int offset, int limit);

    long countByKeyword(String keyword);

    MobileBlack findById(Long id);

    boolean save(MobileBlack mobileBlack);

    boolean update(MobileBlack mobileBlack);

    boolean deleteBatch(List<Long> ids, Long updateId);
}
