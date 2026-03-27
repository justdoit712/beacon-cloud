package com.cz.webmaster.rebuild;

import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.service.CacheRebuildService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                        CacheDomainRegistry.CHANNEL,
                        CacheDomainRegistry.CLIENT_BALANCE
                ),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE
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

        Assert.assertEquals(Arrays.asList(
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE,
                CacheDomainRegistry.CLIENT_SIGN
        ), runner.getCapturedDomains());
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
    public void shouldBuildFailureSummaryWhenBootHandlerThrows() throws Exception {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        CapturingFailureCacheBootReconcileRunner runner = new CapturingFailureCacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(CacheDomainRegistry.CLIENT_BUSINESS),
                buildNoopRebuildService()
        );

        runner.run(null);

        Assert.assertNotNull(runner.getFailureSummary());
        Assert.assertEquals("BOOT", runner.getFailureSummary().getTrigger());
        Assert.assertEquals("ALL", runner.getFailureSummary().getDomain());
        Assert.assertEquals("FAIL", runner.getFailureSummary().getStatus());
        Assert.assertEquals(1, runner.getFailureSummary().getFailCount());
        Assert.assertEquals(Collections.singletonList("boom"), runner.getFailureSummary().getFailedKeys());
    }

    @Test
    public void shouldBuildSkippedSummaryWhenNoExecutableDomains() {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        CacheBootReconcileRunner runner = new CacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(),
                buildNoopRebuildService()
        );

        CacheRebuildReport summaryReport = runner.executeBootReconcile(Collections.emptyList());

        Assert.assertEquals("BOOT", summaryReport.getTrigger());
        Assert.assertEquals("ALL", summaryReport.getDomain());
        Assert.assertEquals("SKIPPED", summaryReport.getStatus());
        Assert.assertEquals(0, summaryReport.getReports().size());
        Assert.assertEquals(0, summaryReport.getSuccessCount());
        Assert.assertEquals(0, summaryReport.getFailCount());
    }

    @Test
    public void shouldAggregateBootSummaryReportAfterExecution() {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        RecordingCacheRebuildService rebuildService = new RecordingCacheRebuildService();
        rebuildService.setDomainReport(CacheDomainRegistry.CLIENT_BUSINESS,
                buildReport(CacheDomainRegistry.CLIENT_BUSINESS, "SUCCESS", 10L, 30L, 2, 2, 0, Collections.emptyList()));
        rebuildService.setDomainReport(CacheDomainRegistry.CHANNEL,
                buildReport(CacheDomainRegistry.CHANNEL, "FAIL", 31L, 50L, 1, 0, 1,
                        Collections.singletonList("channel:7001")));
        CacheBootReconcileRunner runner = new CacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        CacheDomainRegistry.CHANNEL
                ),
                rebuildService
        );

        CacheRebuildReport summaryReport = runner.executeBootReconcile(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CHANNEL
        ));

        Assert.assertEquals("BOOT", summaryReport.getTrigger());
        Assert.assertEquals("ALL", summaryReport.getDomain());
        Assert.assertEquals("PARTIAL", summaryReport.getStatus());
        Assert.assertEquals(2, summaryReport.getReports().size());
        Assert.assertEquals(3, summaryReport.getAttemptedKeys());
        Assert.assertEquals(2, summaryReport.getSuccessCount());
        Assert.assertEquals(1, summaryReport.getFailCount());
        Assert.assertEquals(Collections.singletonList("channel:7001"), summaryReport.getFailedKeys());
    }

    @Test
    public void shouldTreatSuccessDomainWithZeroKeyCountsAsPartialWhenAnotherDomainFails() {
        CacheSyncProperties properties = buildProperties();
        properties.getBoot().setEnabled(true);
        RecordingCacheRebuildService rebuildService = new RecordingCacheRebuildService();
        rebuildService.setDomainReport(CacheDomainRegistry.CLIENT_BUSINESS,
                buildReport(CacheDomainRegistry.CLIENT_BUSINESS, "SUCCESS", 10L, 20L, 0, 0, 0, Collections.emptyList()));
        rebuildService.setDomainReport(CacheDomainRegistry.CHANNEL,
                buildReport(CacheDomainRegistry.CHANNEL, "FAIL", 21L, 40L, 1, 0, 1,
                        Collections.singletonList("channel:8001")));
        CacheBootReconcileRunner runner = new CacheBootReconcileRunner(
                properties,
                buildLoaderRegistry(
                        CacheDomainRegistry.CLIENT_BUSINESS,
                        CacheDomainRegistry.CHANNEL
                ),
                rebuildService
        );

        CacheRebuildReport summaryReport = runner.executeBootReconcile(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CHANNEL
        ));

        Assert.assertEquals("PARTIAL", summaryReport.getStatus());
        Assert.assertEquals(0, summaryReport.getSuccessCount());
        Assert.assertEquals(1, summaryReport.getFailCount());
        Assert.assertEquals(Collections.singletonList("channel:8001"), summaryReport.getFailedKeys());
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

    private CacheRebuildReport buildReport(String domain,
                                           String status,
                                           long startAt,
                                           long endAt,
                                           int attemptedKeys,
                                           int successCount,
                                           int failCount,
                                           List<String> failedKeys) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setDomain(domain);
        report.setTrigger("BOOT");
        report.setStatus(status);
        report.setStartAt(startAt);
        report.setEndAt(endAt);
        report.setAttemptedKeys(attemptedKeys);
        report.setSuccessCount(successCount);
        report.setFailCount(failCount);
        report.setFailedKeys(failedKeys);
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

    private static final class CapturingFailureCacheBootReconcileRunner extends CacheBootReconcileRunner {

        private CacheRebuildReport failureSummary;

        private CapturingFailureCacheBootReconcileRunner(CacheSyncProperties cacheSyncProperties,
                                                         DomainRebuildLoaderRegistry domainRebuildLoaderRegistry,
                                                         CacheRebuildService cacheRebuildService) {
            super(cacheSyncProperties, domainRebuildLoaderRegistry, cacheRebuildService);
        }

        @Override
        protected void onBootEntryReady(List<String> domains) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected CacheRebuildReport handleBootEntryFailure(long startAt,
                                                            List<String> executableDomains,
                                                            Exception ex) {
            this.failureSummary = super.handleBootEntryFailure(startAt, executableDomains, ex);
            return failureSummary;
        }

        private CacheRebuildReport getFailureSummary() {
            return failureSummary;
        }
    }

    private final class RecordingCacheRebuildService implements CacheRebuildService {

        private final List<String> invokedDomains = new ArrayList<>();
        private final Map<String, CacheRebuildReport> domainReports = new LinkedHashMap<>();
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
            if (domainReports.containsKey(domain)) {
                return domainReports.get(domain);
            }
            return buildSuccessReport(domain);
        }

        private List<String> getInvokedDomains() {
            return invokedDomains;
        }

        private void setFailingDomain(String failingDomain) {
            this.failingDomain = failingDomain;
        }

        private void setDomainReport(String domain, CacheRebuildReport report) {
            this.domainReports.put(domain, report);
        }
    }
}
