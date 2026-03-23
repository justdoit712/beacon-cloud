package com.cz.webmaster.rebuild;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.config.CacheSyncProperties;
import com.cz.webmaster.dto.CacheRebuildReport;
import com.cz.webmaster.service.CacheRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 启动校准入口。
 *
 * <p>该类在应用启动完成后接管 {@code sync.boot.*} 配置，
 * 负责判断启动校准入口是否开启、解析请求域、过滤可执行域，
 * 并逐域触发启动阶段缓存重建。</p>
 *
 * <p>启动校准执行失败不会阻塞应用启动。
 * 单个域执行失败时，只记录失败并继续后续域。</p>
 */
@Component
public class CacheBootReconcileRunner implements ApplicationRunner {

    /** 启动校准入口日志。 */
    private static final Logger log = LoggerFactory.getLogger(CacheBootReconcileRunner.class);

    /** 同步配置入口，统一承接 {@code sync.*} 配置。 */
    private final CacheSyncProperties cacheSyncProperties;
    /** 重建 loader 注册表，用于判断域是否具备可执行重建能力。 */
    private final DomainRebuildLoaderRegistry domainRebuildLoaderRegistry;
    /** 缓存重建协调服务，统一复用底层重建引擎。 */
    private final CacheRebuildService cacheRebuildService;

    /**
     * 创建启动校准入口。
     *
     * @param cacheSyncProperties 同步配置
     * @param domainRebuildLoaderRegistry 重建 loader 注册表
     * @param cacheRebuildService 缓存重建协调服务
     */
    public CacheBootReconcileRunner(CacheSyncProperties cacheSyncProperties,
                                    DomainRebuildLoaderRegistry domainRebuildLoaderRegistry,
                                    CacheRebuildService cacheRebuildService) {
        this.cacheSyncProperties = cacheSyncProperties;
        this.domainRebuildLoaderRegistry = domainRebuildLoaderRegistry;
        this.cacheRebuildService = cacheRebuildService;
    }

    /**
     * 在 Spring Boot 启动完成后触发启动校准入口。
     *
     * <p>若入口未开启，则只输出跳过日志；若入口已开启，则解析请求域、
     * 过滤出真正可执行的域，并触发后续执行流程。</p>
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        long startAt = System.currentTimeMillis();
        List<String> executableDomains = Collections.emptyList();
        if (!isBootEntryEnabled()) {
            log.info("cache boot reconcile entry skip: sync.enabled={}, boot.enabled={}",
                    cacheSyncProperties.isEnabled(),
                    cacheSyncProperties.getBoot() != null && cacheSyncProperties.getBoot().isEnabled());
            return;
        }

        try {
            executableDomains = resolveExecutableDomains(resolveRequestedDomains());
            onBootEntryReady(executableDomains);
        } catch (Exception ex) {
            handleBootEntryFailure(startAt, executableDomains, ex);
        }
    }

    /**
     * 判断启动校准入口是否开启。
     *
     * @return true 表示入口可执行
     */
    boolean isBootEntryEnabled() {
        return cacheSyncProperties.isEnabled()
                && cacheSyncProperties.getBoot() != null
                && cacheSyncProperties.getBoot().isEnabled();
    }

    /**
     * 解析本次启动请求的域列表。
     *
     * <p>当 {@code sync.boot.domains} 为空时，使用注册表提供的默认启动校准域；
     * 当存在显式配置时，对域名做去空白、转小写和去重处理。</p>
     *
     * @return 本次启动请求域列表
     */
    List<String> resolveRequestedDomains() {
        CacheSyncProperties.Boot boot = cacheSyncProperties.getBoot();
        if (boot == null || boot.getDomains() == null || boot.getDomains().isEmpty()) {
            return new ArrayList<>(CacheDomainRegistry.currentBootReconcileDomainCodes());
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String domain : boot.getDomains()) {
            if (!StringUtils.hasText(domain)) {
                continue;
            }
            normalized.add(domain.trim().toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            return new ArrayList<>(CacheDomainRegistry.currentBootReconcileDomainCodes());
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 过滤本次请求域列表，保留真正可执行的启动校准域。
     *
     * <p>候选域必须同时满足以下条件：</p>
     * <p>1. 域已注册；</p>
     * <p>2. 域属于当前主线范围；</p>
     * <p>3. 域契约允许启动阶段重建；</p>
     * <p>4. 域已注册重建 loader。</p>
     *
     * @param requestedDomains 本次请求域列表
     * @return 过滤后的可执行域列表
     */
    List<String> resolveExecutableDomains(List<String> requestedDomains) {
        if (requestedDomains == null || requestedDomains.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> executableDomains = new LinkedHashSet<>();
        for (String domain : requestedDomains) {
            if (!isExecutableDomain(domain)) {
                continue;
            }
            executableDomains.add(domain);
        }
        return new ArrayList<>(executableDomains);
    }

    /**
     * 判断单个域是否允许进入启动校准执行范围。
     *
     * @param domain 域编码
     * @return true 表示该域可执行
     */
    boolean isExecutableDomain(String domain) {
        if (!StringUtils.hasText(domain) || !CacheDomainRegistry.contains(domain)) {
            return false;
        }
        if (!CacheDomainRegistry.isCurrentMainlineDomain(domain)) {
            return false;
        }
        if (!CacheDomainRegistry.require(domain).isBootRebuildEnabled()) {
            return false;
        }
        return domainRebuildLoaderRegistry != null && domainRebuildLoaderRegistry.contains(domain);
    }

    /**
     * 启动入口完成域列表解析后的扩展点。
     *
     * <p>当前默认实现会逐域执行启动校准。</p>
     *
     * @param domains 已过滤的可执行域列表
     */
    protected void onBootEntryReady(List<String> domains) {
        executeBootReconcile(domains);
    }

    /**
     * 处理启动入口级别的异常。
     *
     * <p>该异常不会继续向外抛出，但会同时输出错误日志与汇总报告。</p>
     *
     * @param startAt 入口开始时间
     * @param executableDomains 已解析出的可执行域列表
     * @param ex 顶层异常
     * @return 入口失败时生成的汇总报告
     */
    protected CacheRebuildReport handleBootEntryFailure(long startAt, List<String> executableDomains, Exception ex) {
        log.error("cache boot reconcile entry failed without blocking application startup", ex);
        CacheRebuildReport summaryReport = buildEntryFailureSummaryReport(startAt, executableDomains, ex);
        logBootSummary(summaryReport);
        return summaryReport;
    }

    /**
     * 逐域执行启动校准。
     *
     * <p>单个域失败时不会中断后续域，整体失败也不会向外抛出异常。</p>
     *
     * @param domains 已过滤的可执行域列表
     */
    CacheRebuildReport executeBootReconcile(List<String> domains) {
        long startAt = System.currentTimeMillis();
        if (domains == null || domains.isEmpty()) {
            CacheRebuildReport summaryReport = buildBootSummaryReport(startAt, Collections.emptyList());
            log.info("cache boot reconcile entry initialized, executableDomains=[]");
            logBootSummary(summaryReport);
            return summaryReport;
        }

        log.info("cache boot reconcile entry initialized, executableDomains={}", domains);
        List<CacheRebuildReport> domainReports = new ArrayList<>();
        for (String domain : domains) {
            domainReports.add(executeSingleBootDomain(domain));
        }
        CacheRebuildReport summaryReport = buildBootSummaryReport(startAt, domainReports);
        logBootSummary(summaryReport);
        return summaryReport;
    }

    /**
     * 触发单个域的启动阶段重建。
     *
     * @param domain 域编码
     * @return 重建报告
     */
    protected CacheRebuildReport rebuildBootDomain(String domain) {
        return cacheRebuildService.rebuildBootDomain(domain);
    }

    private CacheRebuildReport executeSingleBootDomain(String domain) {
        long startAt = System.currentTimeMillis();
        try {
            CacheRebuildReport report = rebuildBootDomain(domain);
            CacheRebuildReport normalizedReport = normalizeDomainReport(domain, startAt, report);
            logDomainReport(normalizedReport, null);
            return normalizedReport;
        } catch (Exception ex) {
            CacheRebuildReport failureReport = buildFailedBootDomainReport(domain, startAt, ex);
            logDomainReport(failureReport, ex);
            return failureReport;
        }
    }

    private CacheRebuildReport normalizeDomainReport(String domain, long startAt, CacheRebuildReport report) {
        CacheRebuildReport normalized = report == null ? new CacheRebuildReport() : report;
        if (!StringUtils.hasText(normalized.getDomain())) {
            normalized.setDomain(domain);
        }
        if (!StringUtils.hasText(normalized.getTrigger())) {
            normalized.setTrigger("BOOT");
        }
        if (normalized.getStartAt() == null) {
            normalized.setStartAt(startAt);
        }
        if (normalized.getEndAt() == null) {
            normalized.setEndAt(System.currentTimeMillis());
        }
        if (normalized.getFailedKeys() == null) {
            normalized.setFailedKeys(new ArrayList<>());
        }
        return normalized;
    }

    private CacheRebuildReport buildFailedBootDomainReport(String domain, long startAt, Exception ex) {
        CacheRebuildReport report = new CacheRebuildReport();
        report.setTrigger("BOOT");
        report.setDomain(domain);
        report.setStartAt(startAt);
        report.setEndAt(System.currentTimeMillis());
        report.setAttemptedKeys(0);
        report.setSuccessCount(0);
        report.setFailCount(1);
        report.setStatus("FAIL");
        report.setMessage(ex == null ? "boot reconcile failed" : ex.getMessage());
        if (ex == null || !StringUtils.hasText(ex.getMessage())) {
            report.setFailedKeys(Collections.singletonList("boot reconcile failed"));
        } else {
            report.setFailedKeys(Collections.singletonList(ex.getMessage()));
        }
        return report;
    }

    private CacheRebuildReport buildBootSummaryReport(long startAt, List<CacheRebuildReport> domainReports) {
        CacheRebuildReport summaryReport = new CacheRebuildReport();
        summaryReport.setTrigger("BOOT");
        summaryReport.setDomain("ALL");
        summaryReport.setStartAt(startAt);
        summaryReport.setEndAt(System.currentTimeMillis());
        summaryReport.setReports(domainReports == null ? new ArrayList<>() : new ArrayList<>(domainReports));

        int attemptedKeys = 0;
        int successCount = 0;
        int failCount = 0;
        boolean dirtyReplay = false;
        List<String> failedKeys = new ArrayList<>();
        if (domainReports != null) {
            for (CacheRebuildReport domainReport : domainReports) {
                if (domainReport == null) {
                    continue;
                }
                attemptedKeys += domainReport.getAttemptedKeys();
                successCount += domainReport.getSuccessCount();
                failCount += domainReport.getFailCount();
                dirtyReplay = dirtyReplay || domainReport.isDirtyReplay();
                if (domainReport.getFailedKeys() != null && !domainReport.getFailedKeys().isEmpty()) {
                    failedKeys.addAll(domainReport.getFailedKeys());
                }
            }
        }

        summaryReport.setAttemptedKeys(attemptedKeys);
        summaryReport.setSuccessCount(successCount);
        summaryReport.setFailCount(failCount);
        summaryReport.setDirtyReplay(dirtyReplay);
        summaryReport.setFailedKeys(failedKeys);
        if (summaryReport.getReports().isEmpty()) {
            summaryReport.setStatus("SKIPPED");
            summaryReport.setMessage("boot reconcile skipped: no executable domains");
        } else if (failCount == 0) {
            summaryReport.setStatus("SUCCESS");
            summaryReport.setMessage("boot reconcile succeeded");
        } else if (successCount == 0) {
            summaryReport.setStatus("FAIL");
            summaryReport.setMessage("boot reconcile failed");
        } else {
            summaryReport.setStatus("PARTIAL");
            summaryReport.setMessage("boot reconcile partially succeeded");
        }
        return summaryReport;
    }

    private CacheRebuildReport buildEntryFailureSummaryReport(long startAt,
                                                              List<String> executableDomains,
                                                              Exception ex) {
        CacheRebuildReport summaryReport = new CacheRebuildReport();
        summaryReport.setTrigger("BOOT");
        summaryReport.setDomain("ALL");
        summaryReport.setStartAt(startAt);
        summaryReport.setEndAt(System.currentTimeMillis());
        summaryReport.setReports(new ArrayList<>());
        summaryReport.setAttemptedKeys(0);
        summaryReport.setSuccessCount(0);
        summaryReport.setFailCount(1);
        summaryReport.setDirtyReplay(false);
        summaryReport.setStatus("FAIL");
        String message = ex == null || !StringUtils.hasText(ex.getMessage())
                ? "boot reconcile entry failed"
                : ex.getMessage();
        summaryReport.setMessage(message);
        summaryReport.setFailedKeys(Collections.singletonList(message));
        return summaryReport;
    }

    private void logDomainReport(CacheRebuildReport report, Throwable throwable) {
        if (report == null) {
            return;
        }
        String message = "cache boot reconcile domain report: domain={}, status={}, startAt={}, endAt={}, costMs={}, successCount={}, failCount={}, failedKeys={}";
        if (report.getFailCount() > 0) {
            if (throwable == null) {
                log.error(message,
                        report.getDomain(),
                        report.getStatus(),
                        report.getStartAt(),
                        report.getEndAt(),
                        costMs(report),
                        report.getSuccessCount(),
                        report.getFailCount(),
                        report.getFailedKeys());
            } else {
                log.error(message,
                        report.getDomain(),
                        report.getStatus(),
                        report.getStartAt(),
                        report.getEndAt(),
                        costMs(report),
                        report.getSuccessCount(),
                        report.getFailCount(),
                        report.getFailedKeys(),
                        throwable);
            }
            return;
        }
        log.info(message,
                report.getDomain(),
                report.getStatus(),
                report.getStartAt(),
                report.getEndAt(),
                costMs(report),
                report.getSuccessCount(),
                report.getFailCount(),
                report.getFailedKeys());
    }

    private void logBootSummary(CacheRebuildReport summaryReport) {
        if (summaryReport == null) {
            return;
        }
        String message = "cache boot reconcile summary: status={}, startAt={}, endAt={}, costMs={}, domainCount={}, successCount={}, failCount={}, failedKeys={}";
        if (summaryReport.getFailCount() > 0) {
            log.error(message,
                    summaryReport.getStatus(),
                    summaryReport.getStartAt(),
                    summaryReport.getEndAt(),
                    costMs(summaryReport),
                    summaryReport.getReports() == null ? 0 : summaryReport.getReports().size(),
                    summaryReport.getSuccessCount(),
                    summaryReport.getFailCount(),
                    summaryReport.getFailedKeys());
            return;
        }
        log.info(message,
                summaryReport.getStatus(),
                summaryReport.getStartAt(),
                summaryReport.getEndAt(),
                costMs(summaryReport),
                summaryReport.getReports() == null ? 0 : summaryReport.getReports().size(),
                summaryReport.getSuccessCount(),
                summaryReport.getFailCount(),
                summaryReport.getFailedKeys());
    }

    private long costMs(long startAt) {
        return Math.max(0L, System.currentTimeMillis() - startAt);
    }

    private long costMs(CacheRebuildReport report) {
        if (report == null || report.getStartAt() == null || report.getEndAt() == null) {
            return 0L;
        }
        return Math.max(0L, report.getEndAt() - report.getStartAt());
    }
}
