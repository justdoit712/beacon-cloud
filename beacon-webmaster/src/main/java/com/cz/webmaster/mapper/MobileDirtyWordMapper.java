package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.MobileDirtyWord;

/**
 * Data access for {@code mobile_dirtyword} snapshot queries.
 */
public interface MobileDirtyWordMapper {

    /**
     * Load all active dirty words for cache rebuild.
     *
     * @return active dirty words
     */
    java.util.List<MobileDirtyWord> findAllActive();
}
