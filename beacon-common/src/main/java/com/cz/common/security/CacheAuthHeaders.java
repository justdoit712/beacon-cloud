package com.cz.common.security;

/**
 * Shared headers used by cache service auth.
 */
public final class CacheAuthHeaders {

    private CacheAuthHeaders() {
    }

    public static final String HEADER_CALLER = "X-Cache-Caller";
    public static final String HEADER_TIMESTAMP = "X-Cache-Timestamp";
    public static final String HEADER_SIGN = "X-Cache-Sign";

    public static final String REQUEST_ATTR_CALLER = "cache.auth.caller";
}
