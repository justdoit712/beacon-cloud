package com.cz.webmaster.service;

import com.cz.webmaster.entity.CodeLimit;

import java.util.List;

public interface CodeLimitService {

    List<CodeLimit> findListByPage(String keyword, int offset, int limit);

    long countByKeyword(String keyword);

    CodeLimit findById(Long id);

    boolean save(CodeLimit codeLimit);

    boolean update(CodeLimit codeLimit);

    boolean deleteBatch(List<Long> ids, Long updateId);
}
