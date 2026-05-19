package com.cz.webmaster.service;

import com.cz.webmaster.entity.MobileDirtyWord;

import java.util.List;

public interface MobileDirtyWordService {

    List<MobileDirtyWord> findListByPage(String keyword, int offset, int limit);

    long countByKeyword(String keyword);

    MobileDirtyWord findById(Long id);

    boolean save(MobileDirtyWord mobileDirtyWord);

    boolean update(MobileDirtyWord mobileDirtyWord);

    boolean deleteBatch(List<Long> ids, Long updateId);
}
