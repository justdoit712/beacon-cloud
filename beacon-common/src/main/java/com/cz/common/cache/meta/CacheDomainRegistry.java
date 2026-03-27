package com.cz.common.cache.meta;

import com.cz.common.cache.policy.CacheDeletePolicy;
import com.cz.common.cache.policy.CacheRebuildPolicy;
import com.cz.common.cache.policy.CacheWritePolicy;
import com.cz.common.constant.CacheKeyConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存域契约注册表。
 *
 * <p>该类是缓存域元数据的统一收口点，负责维护：</p>
 * <p>1. 系统支持的缓存域契约；</p>
 * <p>2. 主线域、兼容域、手工重建域、启动校准域的边界集合。</p>
 *
 * <p>本类不直接读写 Redis，只提供静态规则与查询能力。</p>
 */
public final class CacheDomainRegistry {

    /** 客户业务配置域。 */
    public static final String CLIENT_BUSINESS = "client_business";
    /** 客户签名域。 */
    public static final String CLIENT_SIGN = "client_sign";
    /** 客户模板域。 */
    public static final String CLIENT_TEMPLATE = "client_template";
    /** 客户通道绑定域。 */
    public static final String CLIENT_CHANNEL = "client_channel";
    /** 通道详情域。 */
    public static final String CHANNEL = "channel";
    /** 黑名单域。 */
    public static final String BLACK = "black";
    /** 敏感词域。 */
    public static final String DIRTY_WORD = "dirty_word";
    /** 携号转网域。 */
    public static final String TRANSFER = "transfer";
    /** 客户余额域。 */
    public static final String CLIENT_BALANCE = "client_balance";

    /** 已注册契约列表（按注册顺序）。 */
    private static final List<CacheDomainContract> CONTRACTS;
    /** 域编码到契约的索引。 */
    private static final Map<String, CacheDomainContract> CONTRACT_MAP;
    /** 当前主线域集合。 */
    private static final Set<String> CURRENT_MAINLINE_DOMAIN_CODES;
    /** 当前兼容保留域集合。 */
    private static final Set<String> CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES;
    /** 当前允许 `ALL` 展开的手工重建域集合。 */
    private static final Set<String> CURRENT_MANUAL_REBUILD_DOMAIN_CODES;
    /** 当前默认启动校准域集合。 */
    private static final Set<String> CURRENT_BOOT_RECONCILE_DOMAIN_CODES;

    static {
        List<CacheDomainContract> contracts = new ArrayList<>();
        registerCurrentMainlineContracts(contracts);
        registerLegacyCompatibleContracts(contracts);

        Map<String, CacheDomainContract> index = new LinkedHashMap<>();
        for (CacheDomainContract contract : contracts) {
            CacheDomainContract previous = index.put(contract.getDomainCode(), contract);
            if (previous != null) {
                throw new IllegalStateException("duplicate domain contract: " + contract.getDomainCode());
            }
        }

        CONTRACTS = Collections.unmodifiableList(contracts);
        CONTRACT_MAP = Collections.unmodifiableMap(index);

        CURRENT_MAINLINE_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_BUSINESS,
                CLIENT_SIGN,
                CLIENT_TEMPLATE,
                CLIENT_CHANNEL,
                CHANNEL,
                CLIENT_BALANCE,
                TRANSFER,
                BLACK,
                DIRTY_WORD
        )));

        CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>());

        CURRENT_MANUAL_REBUILD_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_BUSINESS,
                CLIENT_SIGN,
                CLIENT_TEMPLATE,
                CLIENT_CHANNEL,
                CHANNEL,
                CLIENT_BALANCE,
                TRANSFER,
                BLACK,
                DIRTY_WORD
        )));

        CURRENT_BOOT_RECONCILE_DOMAIN_CODES = Collections.unmodifiableSet(
                buildCurrentBootReconcileDomainCodes(CONTRACT_MAP, CURRENT_MANUAL_REBUILD_DOMAIN_CODES)
        );
    }

    private CacheDomainRegistry() {
    }

    /**
     * 返回全部已注册契约。
     *
     * @return 只读契约列表
     */
    public static List<CacheDomainContract> list() {
        return CONTRACTS;
    }

    /**
     * 返回全部已注册域编码。
     *
     * @return 域编码集合
     */
    public static Set<String> domainCodes() {
        return CONTRACT_MAP.keySet();
    }

    /**
     * 按域编码查询契约。
     *
     * @param domainCode 域编码
     * @return 契约对象；未命中返回 {@code null}
     */
    public static CacheDomainContract get(String domainCode) {
        return CONTRACT_MAP.get(domainCode);
    }

    /**
     * 按域编码查询契约（强校验）。
     *
     * @param domainCode 域编码
     * @return 契约对象
     * @throws IllegalArgumentException 当域未注册时抛出
     */
    public static CacheDomainContract require(String domainCode) {
        CacheDomainContract contract = get(domainCode);
        if (contract == null) {
            throw new IllegalArgumentException("unsupported cache domain: " + domainCode);
        }
        return contract;
    }

    /**
     * 判断域是否已注册。
     *
     * @param domainCode 域编码
     * @return true 表示已注册
     */
    public static boolean contains(String domainCode) {
        return CONTRACT_MAP.containsKey(domainCode);
    }

    /**
     * 当前主线域集合。
     *
     * @return 主线域集合
     */
    public static Set<String> currentMainlineDomainCodes() {
        return CURRENT_MAINLINE_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前主线范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于主线域
     */
    public static boolean isCurrentMainlineDomain(String domainCode) {
        return CURRENT_MAINLINE_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 当前兼容保留域集合。
     *
     * @return 兼容保留域集合
     */
    public static Set<String> currentLegacyCompatibleDomainCodes() {
        return CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前兼容保留范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于兼容保留域
     */
    public static boolean isCurrentLegacyCompatibleDomain(String domainCode) {
        return CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 当前允许由 `ALL` 展开的手工重建域集合。
     *
     * @return 手工重建域集合
     */
    public static Set<String> currentManualRebuildDomainCodes() {
        return CURRENT_MANUAL_REBUILD_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前手工重建允许范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于允许范围
     */
    public static boolean isCurrentManualRebuildDomain(String domainCode) {
        return CURRENT_MANUAL_REBUILD_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 当前默认启动校准域集合。
     *
     * @return 启动校准域集合
     */
    public static Set<String> currentBootReconcileDomainCodes() {
        return CURRENT_BOOT_RECONCILE_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前默认启动校准范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于默认启动校准范围
     */
    public static boolean isCurrentBootReconcileDomain(String domainCode) {
        return CURRENT_BOOT_RECONCILE_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 基于手工重建域集合推导默认启动校准域集合。
     *
     * <p>仅当一个域同时满足：</p>
     * <p>1. 在 manual 默认范围内；</p>
     * <p>2. 契约允许 boot rebuild；</p>
     * <p>才会进入 boot 默认范围。</p>
     *
     * @param contractMap 契约索引
     * @param manualDomains manual 默认域
     * @return boot 默认域集合
     */
    private static Set<String> buildCurrentBootReconcileDomainCodes(Map<String, CacheDomainContract> contractMap,
                                                                    Set<String> manualDomains) {
        Set<String> result = new LinkedHashSet<>();
        for (String domainCode : manualDomains) {
            CacheDomainContract contract = contractMap.get(domainCode);
            if (contract != null && contract.isBootRebuildEnabled()) {
                result.add(domainCode);
            }
        }
        return result;
    }

    /**
     * 注册当前主线缓存域契约。
     *
     * @param contracts 契约可变列表
     */
    private static void registerCurrentMainlineContracts(List<CacheDomainContract> contracts) {
        contracts.add(new CacheDomainContract(
                CLIENT_BUSINESS,
                Collections.singletonList(CacheKeyConstants.CLIENT_BUSINESS + "{apikey}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.WRITE_THROUGH,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                CLIENT_SIGN,
                Collections.singletonList(CacheKeyConstants.CLIENT_SIGN + "{clientId}"),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.DELETE_AND_REBUILD,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                CLIENT_TEMPLATE,
                Collections.singletonList(CacheKeyConstants.CLIENT_TEMPLATE + "{signId}"),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.DELETE_AND_REBUILD,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                CLIENT_CHANNEL,
                Collections.singletonList(CacheKeyConstants.CLIENT_CHANNEL + "{clientId}"),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.DELETE_AND_REBUILD,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                CHANNEL,
                Collections.singletonList(CacheKeyConstants.CHANNEL + "{id}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.WRITE_THROUGH,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                CLIENT_BALANCE,
                Collections.singletonList(CacheKeyConstants.CLIENT_BALANCE + "{clientId}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.MYSQL_ATOMIC_UPDATE_THEN_REFRESH,
                CacheDeletePolicy.OVERWRITE_ONLY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                TRANSFER,
                Collections.singletonList(CacheKeyConstants.TRANSFER + "{mobile}"),
                CacheRedisType.STRING,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.WRITE_THROUGH,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                BLACK,
                Arrays.asList(
                        CacheKeyConstants.BLACK + "{mobile}",
                        CacheKeyConstants.BLACK + "{clientId}" + CacheKeyConstants.SEPARATE + "{mobile}"
                ),
                CacheRedisType.STRING,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.WRITE_THROUGH,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        contracts.add(new CacheDomainContract(
                DIRTY_WORD,
                Collections.singletonList(CacheKeyConstants.DIRTY_WORD),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.DELETE_AND_REBUILD,
                CacheDeletePolicy.DELETE_KEY,
                CacheRebuildPolicy.FULL_REBUILD,
                "beacon-webmaster",
                true
        ));
    }

    /**
     * 注册当前兼容保留缓存域契约。
     *
     * @param contracts 契约可变列表
     */
    private static void registerLegacyCompatibleContracts(List<CacheDomainContract> contracts) {
        // no legacy compatible domain currently
    }
}
