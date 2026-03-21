package com.cz.webmaster.rebuild;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 域级缓存重建加载器注册表。
 *
 * <p>负责维护缓存域与 {@link DomainRebuildLoader} 的映射关系，
 * 为手工重建入口提供统一的 loader 查询能力。</p>
 */
@Component
public class DomainRebuildLoaderRegistry {

    private final List<DomainRebuildLoader> loaders;
    private final Map<String, DomainRebuildLoader> loaderIndex;

    public DomainRebuildLoaderRegistry(List<DomainRebuildLoader> loaders) {
        List<DomainRebuildLoader> safeLoaders = loaders == null ? Collections.emptyList() : loaders;
        Map<String, DomainRebuildLoader> index = new LinkedHashMap<>();
        for (DomainRebuildLoader loader : safeLoaders) {
            if (loader == null) {
                continue;
            }
            String domainCode = normalizeDomain(loader.domainCode());
            DomainRebuildLoader previous = index.put(domainCode, loader);
            if (previous != null) {
                throw new IllegalStateException("duplicate rebuild loader for domain: " + domainCode);
            }
        }
        this.loaders = Collections.unmodifiableList(new ArrayList<>(index.values()));
        this.loaderIndex = Collections.unmodifiableMap(index);
    }

    /**
     * 返回全部已注册加载器。
     *
     * @return 只读加载器列表
     */
    public List<DomainRebuildLoader> list() {
        return loaders;
    }

    /**
     * 返回全部已注册的缓存域编码。
     *
     * @return 只读缓存域编码集合
     */
    public Set<String> domainCodes() {
        return loaderIndex.keySet();
    }

    /**
     * 根据缓存域编码查询加载器。
     *
     * @param domainCode 缓存域编码
     * @return 加载器；未命中时返回 {@code null}
     */
    public DomainRebuildLoader get(String domainCode) {
        if (!StringUtils.hasText(domainCode)) {
            return null;
        }
        return loaderIndex.get(normalizeDomain(domainCode));
    }

    /**
     * 判断指定缓存域是否已注册加载器。
     *
     * @param domainCode 缓存域编码
     * @return true 表示已注册，false 表示未注册
     */
    public boolean contains(String domainCode) {
        return get(domainCode) != null;
    }

    private String normalizeDomain(String domainCode) {
        if (!StringUtils.hasText(domainCode)) {
            throw new IllegalArgumentException("rebuild loader domainCode must not be blank");
        }
        return domainCode.trim().toLowerCase(Locale.ROOT);
    }
}
