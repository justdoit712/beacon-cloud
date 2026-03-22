package com.cz.webmaster.rebuild;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.config.CacheSyncProperties;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CacheBootReconcileRunnerTest {

    @Test
    public void shouldSkipEntryWhenBootDisabled() throws Exception {
        CacheSyncProperties properties = buildProperties();
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(properties);

        runner.run(null);

        Assert.assertFalse(runner.isBootEntryEnabled());
        Assert.assertNull(runner.getCapturedDomains());
    }

    @Test
    public void shouldUseDefaultDomainsWhenBootEnabledWithoutConfiguredDomains() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(properties);

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL
        ), runner.getCapturedDomains());
    }

    @Test
    public void shouldNormalizeConfiguredDomainsWhenBootEnabled() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        properties.getBoot().setDomains(Arrays.asList(" Channel ", "client_business", "CHANNEL", " "));
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(properties);

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BUSINESS
        ), runner.getCapturedDomains());
    }

    @Test
    public void shouldSkipEntryWhenSyncDisabled() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.setEnabled(false);
        properties.getBoot().setEnabled(true);
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(properties);

        runner.run(null);

        Assert.assertFalse(runner.isBootEntryEnabled());
        Assert.assertNull(runner.getCapturedDomains());
    }

    private CacheSyncProperties buildProperties() {
        return new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
    }

    private static final class CapturingCacheBootReconcileRunner extends CacheBootReconcileRunner {

        private List<String> capturedDomains;

        private CapturingCacheBootReconcileRunner(CacheSyncProperties cacheSyncProperties) {
            super(cacheSyncProperties);
        }

        @Override
        protected void onBootEntryReady(List<String> domains) {
            this.capturedDomains = new ArrayList<>(domains);
        }

        private List<String> getCapturedDomains() {
            return capturedDomains;
        }
    }
}
