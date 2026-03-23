package com.cz.webmaster.rebuild;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.service.CacheRebuildService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CacheBootReconcileRunnerTest {

    @Test
    public void shouldSkipEntryWhenBootDisabled() throws Exception {
        CacheSyncProperties properties = buildProperties();
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(CacheDomainRegistry.CLIENT_BUSINESS),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertFalse(runner.isBootEntryEnabled());
        Assert.assertNull(runner.getCapturedDomains());
    }

    @Test
    public void shouldUseDefaultDomainsWhenBootEnabledWithoutConfiguredDomains() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        CacheDomainRegistry.CLIENT_CHANNEL,
                        CacheDomainRegistry.CHANNEL
                ),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL
        ), runner.getCapturedDomains());
    }

    @Test
    public void shouldFilterConfiguredDomainsByBootRules() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        properties.getBoot().setDomains(Arrays.asList(
                " Channel ",
                "client_business",
                "client_balance",
                "client_sign",
                "unknown_domain",
                "CHANNEL",
                " "
        ));
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CHANNEL,
                        CacheDomainRegistry.CLIENT_BALANCE,
                        CacheDomainRegistry.CLIENT_SIGN
                ),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertEquals(Collections.singletonList(CacheDomainRegistry.CHANNEL), runner.getCapturedDomains());
    }

    @Test
    public void shouldFilterDefaultDomainsWithoutRegisteredLoader() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        CacheDomainRegistry.CHANNEL
                ),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CHANNEL
        ), runner.getCapturedDomains());
    }

    @Test
    public void shouldSkipEntryWhenSyncDisabled() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.setEnabled(false);
        properties.getBoot().setEnabled(true);
        CapturingCacheBootReconcileRunner runner = new CapturingCacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(CacheDomainRegistry.CLIENT_BUSINESS),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertFalse(runner.isBootEntryEnabled());
        Assert.assertNull(runner.getCapturedDomains());
    }

    @Test
    public void shouldExecuteBootRebuildForEachExecutableDomain() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        RecordingCacheRebuildService rebuildService = new RecordingCacheRebuildService();
        CacheBootReconcileRunner runner = new CacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        CacheDomainRegistry.CHANNEL
                ),
                rebuildService
        );

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CHANNEL
        ), rebuildService.getInvokedDomains());
    }

    @Test
    public void shouldContinueWhenSingleDomainBootRebuildFails() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        properties.getBoot().setDomains(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CHANNEL
        ));
        RecordingCacheRebuildService rebuildService = new RecordingCacheRebuildService();
        rebuildService.setFailingDomain(CacheDomainRegistry.CLIENT_BUSINESS);
        CacheBootReconcileRunner runner = new CacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        CacheDomainRegistry.CHANNEL
                ),
                rebuildService
        );

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CHANNEL
        ), rebuildService.getInvokedDomains());
    }

    @Test
    public void shouldNotPropagateUnexpectedFailureFromBootHandler() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        CacheBootReconcileRunner runner = new CacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(CacheDomainRegistry.CLIENT_BUSINESS),
                buildNoopRebuildService()
        ) {
            @Override
            protected void onBootEntryReady(List<String> domains) {
                throw new IllegalStateException("boom");
            }
        };

        runner.run(null);
    }

    private CacheSyncProperties buildProperties() {
        return new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
    }

    private DomainRebuildLoaderRegistry buildLoaderRegistry(String... domains) {
        List<DomainRebuildLoader> loaders = new ArrayList<>();
        for (String domain : domains) {
            loaders.add(stubLoader(domain));
        }
        return new DomainRebuildLoaderRegistry(loaders);
    }

    private DomainRebuildLoader stubLoader(String domainCode) {
        return new DomainRebuildLoader() {
            @Override
            public String domainCode() {
                return domainCode;
            }

            @Override
            public List<Object> loadSnapshot() {
                return Collections.emptyList();
            }
        };
    }

    private CacheRebuildService buildNoopRebuildService() {
        return new CacheRebuildService() {
            @Override
            public CacheRebuildReport rebuildDomain(String domain) {
                return buildSuccessReport(domain);
            }

            @Override
            public CacheRebuildReport rebuildBootDomain(String domain) {
                return buildSuccessReport(domain);
            }
        };
    }

    private CacheRebuildReport buildSuccessReport(String domain) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setDomain(domain);
        report.setTrigger("BOOT");
        report.setStatus("SUCCESS");
        report.setSuccessCount(0);
        report.setFailCount(0);
        return report;
    }

    private static final class CapturingCacheBootReconcileRunner extends CacheBootReconcileRunner {

        private List<String> capturedDomains;

        private CapturingCacheBootReconcileRunner(CacheSyncProperties cacheSyncProperties,
                                                  DomainRebuildLoaderRegistry domainRebuildLoaderRegistry,
                                                  CacheRebuildService cacheRebuildService) {
            super(cacheSyncProperties, domainRebuildLoaderRegistry, cacheRebuildService);
        }

        @Override
        protected void onBootEntryReady(List<String> domains) {
            this.capturedDomains = new ArrayList<>(domains);
        }

        private List<String> getCapturedDomains() {
            return capturedDomains;
        }
    }

    private final class RecordingCacheRebuildService implements CacheRebuildService {

        private final List<String> invokedDomains = new ArrayList<>();
        private String failingDomain;

        @Override
        public CacheRebuildReport rebuildDomain(String domain) {
            return buildSuccessReport(domain);
        }

        @Override
        public CacheRebuildReport rebuildBootDomain(String domain) {
            invokedDomains.add(domain);
            if (domain.equals(failingDomain)) {
                throw new IllegalStateException("domain failed");
            }
            return buildSuccessReport(domain);
        }

        private List<String> getInvokedDomains() {
            return invokedDomains;
        }

        private void setFailingDomain(String failingDomain) {
            this.failingDomain = failingDomain;
        }
    }
}
