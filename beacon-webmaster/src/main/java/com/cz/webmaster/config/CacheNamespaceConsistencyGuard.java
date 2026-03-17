package com.cz.webmaster.config;

import org.springframework.stereotype.Component;

/**
 * Marker component for namespace consistency validation.
 *
 * <p>The actual validation is performed inside {@link CacheSyncProperties}
 * during configuration initialization. This class exists to make the guard
 * explicit in the configuration layer.</p>
 */
@Component
public class CacheNamespaceConsistencyGuard {

    public CacheNamespaceConsistencyGuard(CacheSyncProperties cacheSyncProperties) {
        // Intentionally empty.
        // Dependency injection guarantees CacheSyncProperties is initialized
        // and validated during startup.
    }
}
