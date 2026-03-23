package com.cz.common.constant;

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
 * <p>这个类是缓存域元数据的唯一收口点，负责把“系统支持哪些缓存域、每个域的基础契约是什么、
 * 哪些域属于当前主线、哪些域允许手工重建、哪些域允许启动校准”等静态规则统一固化下来。</p>
 *
 * <p>职责只有两项：</p>
 * <p>1. 注册系统已知的缓存域契约；</p>
 * <p>2. 提供统一的查询入口。</p>
 *
 * <p>该类不负责实际读写 Redis，也不负责业务装配逻辑。运行时同步、手工重建、启动校准
 * 都应以本类提供的范围判定与契约查询结果为准，而不是各自维护一套独立的域清单。</p>
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

    /** 已注册契约列表，保持注册顺序。 */
    private static final List<CacheDomainContract> CONTRACTS;
    /** 域编码到契约的索引。 */
    private static final Map<String, CacheDomainContract> CONTRACT_MAP;
    /** 当前主线域集合。 */
    private static final Set<String> CURRENT_MAINLINE_DOMAIN_CODES;
    /** 当前兼容保留域集合。 */
    private static final Set<String> CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES;
    /** 当前允许 ALL 展开的手工重建域集合。 */
    private static final Set<String> CURRENT_MANUAL_REBUILD_DOMAIN_CODES;

    /**
     * 当前允许进入默认启动校准范围的域集合。
     *
     * <p>该集合不是“所有 bootRebuildEnabled=true 的域”，而是更严格的交集：
     * 既要属于默认手工重建域，又要在域契约上显式允许启动阶段重建。
     * 这样可以保证启动校准与手工重建使用一致的默认域边界，也不会误带仍需额外前置条件的域。</p>
     */
    private static final Set<String> CURRENT_BOOT_RECONCILE_DOMAIN_CODES;

    /**
     * 初始化注册表。
     *
     * <p>初始化顺序固定如下：</p>
     * <p>1. 先注册主线域与兼容保留域契约；</p>
     * <p>2. 再构建按域编码索引的只读映射；</p>
     * <p>3. 最后派生主线域、兼容域、默认手工重建域、默认启动校准域等只读集合。</p>
     *
     * <p>若发现重复注册同一个域编码，会在类初始化阶段直接失败，避免系统带着歧义配置启动。</p>
     */
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
                CLIENT_CHANNEL,
                CHANNEL,
                CLIENT_BALANCE
        )));
        CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_SIGN,
                CLIENT_TEMPLATE,
                BLACK,
                DIRTY_WORD,
                TRANSFER
        )));
        CURRENT_MANUAL_REBUILD_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_BUSINESS,
                CLIENT_CHANNEL,
                CHANNEL,
                CLIENT_BALANCE
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
     * @return 契约对象；未命中时返回 {@code null}
     */
    public static CacheDomainContract get(String domainCode) {
        return CONTRACT_MAP.get(domainCode);
    }

    /**
     * 按域编码查询契约。
     *
     * <p>这是强校验版本：如果域未注册，则直接抛出异常。</p>
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
     * 判断域编码是否已经注册。
     *
     * @param domainCode 域编码
     * @return true 表示已注册，false 表示未注册
     */
    public static boolean contains(String domainCode) {
        return CONTRACT_MAP.containsKey(domainCode);
    }

    /**
     * 返回当前主线域集合。
     *
     * @return 当前主线域集合
     */
    public static Set<String> currentMainlineDomainCodes() {
        return CURRENT_MAINLINE_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前主线范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于当前主线域
     */
    public static boolean isCurrentMainlineDomain(String domainCode) {
        return CURRENT_MAINLINE_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 返回当前兼容保留域集合。
     *
     * @return 当前兼容保留域集合
     */
    public static Set<String> currentLegacyCompatibleDomainCodes() {
        return CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前兼容保留范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于当前兼容保留域
     */
    public static boolean isCurrentLegacyCompatibleDomain(String domainCode) {
        return CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 返回当前允许由 ALL 展开的手工重建域集合。
     *
     * <p>当前只包含已纳入主线且允许进入默认手工重建范围的域。</p>
     *
     * @return 当前允许 ALL 展开的手工重建域集合
     */
    public static Set<String> currentManualRebuildDomainCodes() {
        return CURRENT_MANUAL_REBUILD_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前允许 ALL 展开的手工重建范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于当前手工重建允许范围
     */
    public static boolean isCurrentManualRebuildDomain(String domainCode) {
        return CURRENT_MANUAL_REBUILD_DOMAIN_CODES.contains(domainCode);
    }

    /**
     * 返回当前允许进入默认启动校准范围的域集合。
     *
     * @return 当前允许进入默认启动校准范围的域集合
     */
    public static Set<String> currentBootReconcileDomainCodes() {
        return CURRENT_BOOT_RECONCILE_DOMAIN_CODES;
    }

    /**
     * 判断域是否属于当前允许进入默认启动校准范围。
     *
     * @param domainCode 域编码
     * @return true 表示属于当前默认启动校准范围
     */
    public static boolean isCurrentBootReconcileDomain(String domainCode) {
        return CURRENT_BOOT_RECONCILE_DOMAIN_CODES.contains(domainCode);
    }

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
    }

    private static void registerLegacyCompatibleContracts(List<CacheDomainContract> contracts) {
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
    }
}
