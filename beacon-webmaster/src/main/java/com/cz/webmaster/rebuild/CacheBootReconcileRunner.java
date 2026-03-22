package com.cz.webmaster.rebuild;

import com.cz.common.constant.CacheDomainRegistry;
import com.cz.webmaster.config.CacheSyncProperties;
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
 * 负责判断启动校准入口是否开启，并解析、过滤本次启动请求的域列表。</p>
 *
 * <p>当前类只承担入口接线、域列表标准化和可执行域筛选职责，
 * 不在这里实现逐域重建和汇总逻辑。</p>
 */
@Component
public class CacheBootReconcileRunner implements ApplicationRunner {

    /** 启动校准入口日志。 */
    private static final Logger log = LoggerFactory.getLogger(CacheBootReconcileRunner.class);

    /** 同步配置入口，统一承接 {@code sync.*} 配置。 */
    private final CacheSyncProperties cacheSyncProperties;
    /** 重建 loader 注册表，用于判断域是否具备可执行重建能力。 */
    private final DomainRebuildLoaderRegistry domainRebuildLoaderRegistry;

    /**
     * 创建启动校准入口。
     *
     * @param cacheSyncProperties 同步配置
     * @param domainRebuildLoaderRegistry 重建 loader 注册表
     */
    public CacheBootReconcileRunner(CacheSyncProperties cacheSyncProperties,
                                    DomainRebuildLoaderRegistry domainRebuildLoaderRegistry) {
        this.cacheSyncProperties = cacheSyncProperties;
        this.domainRebuildLoaderRegistry = domainRebuildLoaderRegistry;
    }

    /**
     * 在 Spring Boot 启动完成后触发启动校准入口。
     *
     * <p>若入口未开启，则仅输出跳过日志；若入口已开启，
     * 则解析请求域列表、过滤出真正可执行的域，并交给后续处理钩子。</p>
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
        onBootEntryReady(resolveExecutableDomains(resolveRequestedDomains()));
    }

    /**
     * 判断启动校准入口是否开启。
     *
     * <p>只有同步总开关与启动校准开关同时开启时，
     * 启动入口才允许继续执行。</p>
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
     * <p>当前默认实现仅输出日志，后续可以在此处接入实际的启动校准执行流程。</p>
     *
     * @param domains 已过滤的可执行域列表
     */
    protected void onBootEntryReady(List<String> domains) {
        log.info("cache boot reconcile entry initialized, executableDomains={}", domains);
    }
}
