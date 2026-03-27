package com.cz.webmaster.mapper;

import java.util.List;
import java.util.Map;

/**
 * Data access for {@code client_sign} snapshot queries.
 */
public interface ClientSignMapper {

    /**
     * Load all active sign rows for cache rebuild.
     *
     * @return active sign rows
     */
    List<Map<String, Object>> findAllActive();
}
