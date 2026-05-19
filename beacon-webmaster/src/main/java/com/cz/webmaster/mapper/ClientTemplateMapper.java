package com.cz.webmaster.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * Data access for {@code client_template} snapshot queries.
 */
public interface ClientTemplateMapper {

    /**
     * Load all active template rows for cache rebuild.
     *
     * @return active template rows
     */
    List<Map<String, Object>> findAllActive();

    long countByKeyword(@Param("keyword") String keyword);

    List<Map<String, Object>> findPage(@Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    Map<String, Object> findById(@Param("id") Long id);

    List<Map<String, Object>> findActiveMembersBySignId(@Param("signId") Long signId);

    int insert(Map<String, Object> row);

    int update(Map<String, Object> row);

    int logicalDelete(@Param("id") Long id, @Param("operatorId") Long operatorId);
}
