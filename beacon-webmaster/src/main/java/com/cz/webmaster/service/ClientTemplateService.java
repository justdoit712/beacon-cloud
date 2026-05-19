package com.cz.webmaster.service;

import java.util.List;
import java.util.Map;

public interface ClientTemplateService {

    long countByKeyword(String keyword);

    List<Map<String, Object>> findPage(String keyword, int offset, int limit);

    Map<String, Object> findById(Long id);

    boolean save(Map<String, Object> body, Long operatorId);

    boolean update(Map<String, Object> body, Long operatorId);

    boolean deleteBatch(List<Long> ids, Long operatorId);
}
