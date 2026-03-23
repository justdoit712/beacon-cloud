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
        if (!isBootEntryEnabled()) {
            log.info("cache boot reconcile entry skip: sync.enabled={}, boot.enabled={}",
                    cacheSyncProperties.isEnabled(),
                    cacheSyncProperties.getBoot() != null && cacheSyncProperties.getBoot().isEnabled());
            return;
        }

        try {
            onBootEntryReady(resolveExecutableDomains(resolveRequestedDomains()));
        } catch (Exception ex) {
            log.error("cache boot reconcile entry failed without blocking application startup", ex);
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
     * 逐域执行启动校准。
     *
     * <p>单个域失败时不会中断后续域，整体失败也不会向外抛出异常。</p>
     *
     * @param domains 已过滤的可执行域列表
     */
    void executeBootReconcile(List<String> domains) {
        if (domains == null || domains.isEmpty()) {
            log.info("cache boot reconcile entry initialized, executableDomains=[]");
            return;
        }

        log.info("cache boot reconcile entry initialized, executableDomains={}", domains);
        for (String domain : domains) {
            long startAt = System.currentTimeMillis();
            try {
                CacheRebuildReport report = rebuildBootDomain(domain);
                log.info("cache boot reconcile domain finished: domain={}, status={}, successCount={}, failCount={}, costMs={}",
                        domain,
                        report == null ? "-" : report.getStatus(),
                        report == null ? 0 : report.getSuccessCount(),
                        report == null ? 0 : report.getFailCount(),
                        costMs(startAt));
            } catch (Exception ex) {
                log.error("cache boot reconcile domain failed: domain={}, costMs={}", domain, costMs(startAt), ex);
            }
        }
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

    private long costMs(long startAt) {
        return Math.max(0L, System.currentTimeMillis() - startAt);
    }
}
