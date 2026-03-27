package com.cz.webmaster.mapper;

import com.cz.webmaster.entity.MobileBlack;

/**
 * Data access for {@code mobile_black} snapshot queries.
 */
public interface MobileBlackMapper {

    /**
     * Load all active black list rows for cache rebuild.
     *
     * @return active black list rows
     */
    java.util.List<MobileBlack> findAllActive();
}
