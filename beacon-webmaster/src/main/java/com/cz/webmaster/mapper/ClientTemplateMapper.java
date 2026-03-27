package com.cz.webmaster.mapper;

import java.util.List;
import java.util.Map;

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
}
