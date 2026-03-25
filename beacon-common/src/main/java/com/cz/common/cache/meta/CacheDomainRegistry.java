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
        // 先把当前代码中已经正式注册的缓存域契约全部装配出来。
        // 这里分两批注册：
        // 1. 当前架构演进的主线域；
        // 2. 仍需兼容、但不属于当前主线重点的保留域。
        List<CacheDomainContract> contracts = new ArrayList<>();
        registerCurrentMainlineContracts(contracts);
        registerLegacyCompatibleContracts(contracts);

        // 基于 domainCode 构建只读索引。
        // 若同一个域被重复注册，直接在类初始化阶段失败，避免后续范围判断和契约查询出现歧义。
        Map<String, CacheDomainContract> index = new LinkedHashMap<>();
        for (CacheDomainContract contract : contracts) {
            CacheDomainContract previous = index.put(contract.getDomainCode(), contract);
            if (previous != null) {
                throw new IllegalStateException("duplicate domain contract: " + contract.getDomainCode());
            }
        }

        CONTRACTS = Collections.unmodifiableList(contracts);
        CONTRACT_MAP = Collections.unmodifiableMap(index);

        // 当前主线域集合。
        // 这组域表示“当前缓存一致性架构演进的主战场”，
        // 后续运行时同步、手工重建、启动校准等设计优先围绕这几个域展开。
        CURRENT_MAINLINE_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_BUSINESS,
                CLIENT_CHANNEL,
                CHANNEL,
                CLIENT_BALANCE
        )));

        // 当前兼容保留域集合。
        // 这组域说明代码和缓存能力仍要兼容它们，
        // 但它们不是当前四层架构演进里的主线重点对象。
        CURRENT_LEGACY_COMPATIBLE_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_SIGN,
                CLIENT_TEMPLATE,
                BLACK,
                DIRTY_WORD,
                TRANSFER
        )));

        // 当前允许由手工重建入口 `ALL` 自动展开的域集合。
        // 也就是说，当外部请求手工重建 `ALL` 时，默认只会展开到这几个域，
        // 而不会把所有已注册域都无差别纳入。
        CURRENT_MANUAL_REBUILD_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_BUSINESS,
                CLIENT_CHANNEL,
                CHANNEL,
                CLIENT_BALANCE
        )));

        // 当前默认启动校准范围。
        // 该集合不是手工写死另一份列表，而是基于“允许 ALL 展开的手工重建域”
        // 再叠加“域契约允许 boot rebuild”规则推导出来，
        // 从而保证 boot 与 manual 在默认边界上保持一致。
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

    /**
     * 基于“默认手工重建域集合”推导当前默认启动校准域集合。
     *
     * <p>这里的设计目标不是再手工维护一份 boot 域列表，
     * 而是复用 manual 的默认边界，再额外叠加“域契约允许 boot rebuild”这一层过滤。</p>
     *
     * <p>因此最终结果可以理解为：</p>
     * <p>1. 先从当前允许由 {@code ALL} 展开的手工重建域开始；</p>
     * <p>2. 再逐个检查这些域在契约上是否显式允许进入启动校准；</p>
     * <p>3. 只有同时满足这两个条件的域，才会进入默认 boot 范围。</p>
     *
     * @param contractMap 域编码到契约的索引
     * @param manualDomains 当前允许由 {@code ALL} 展开的默认手工重建域集合
     * @return 当前默认启动校准域集合
     */
    private static Set<String> buildCurrentBootReconcileDomainCodes(Map<String, CacheDomainContract> contractMap,
                                                                    Set<String> manualDomains) {
        Set<String> result = new LinkedHashSet<>();
        for (String domainCode : manualDomains) {
            // 先从默认 manual 域中取候选项，再检查该域契约是否允许 boot rebuild。
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
     * <p>这里注册的是当前四层缓存一致性架构的核心域，也就是：</p>
     * <p>1. 运行时同步优先覆盖的域；</p>
     * <p>2. `ALL` 手工重建默认会带上的域；</p>
     * <p>3. 默认启动校准优先考虑的域。</p>
     *
     * <p>每一次 `contracts.add(new CacheDomainContract(...))`，
     * 都是在为一个缓存域声明完整契约，包括：</p>
     * <p>1. 域编码；</p>
     * <p>2. 逻辑 key 模式；</p>
     * <p>3. Redis 结构；</p>
     * <p>4. 真源；</p>
     * <p>5. 写入、删除、重建策略；</p>
     * <p>6. 归属服务；</p>
     * <p>7. 是否允许启动校准。</p>
     *
     * @param contracts 用于承接当前已注册缓存域契约的可变列表
     */
    private static void registerCurrentMainlineContracts(List<CacheDomainContract> contracts) {
        // client_business：客户业务配置域。
        // 特点：
        // 1. 使用 apiKey 作为逻辑 key 后缀；
        // 2. Redis 中按 HASH 保存整条客户业务配置；
        // 3. MySQL 为真源；
        // 4. 正常写路径采用写穿覆盖；
        // 5. 允许手工重建，也允许进入默认启动校准。
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

        // client_channel：客户通道路由集合域。
        // 特点：
        // 1. 使用 clientId 作为逻辑 key 后缀；
        // 2. Redis 中按 SET 保存该客户当前全部有效路由成员；
        // 3. 因为成员可能增删变化，写入策略使用“删后整组重建”；
        // 4. 允许手工重建，也允许进入默认启动校准。
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

        // channel：通道主数据域。
        // 特点：
        // 1. 使用通道 id 作为逻辑 key 后缀；
        // 2. Redis 中按 HASH 保存通道字段映射；
        // 3. MySQL 为真源；
        // 4. 正常写路径采用写穿覆盖；
        // 5. 允许手工重建，也允许进入默认启动校准。
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

        // client_balance：客户余额镜像域。
        // 特点：
        // 1. 使用 clientId 作为逻辑 key 后缀；
        // 2. Redis 中按 HASH 保存余额镜像；
        // 3. MySQL 为余额真源；
        // 4. 写入策略不是普通写穿，而是“先 MySQL 原子更新，再刷新 Redis 镜像”；
        // 5. 删除策略为 OVERWRITE_ONLY，避免高风险域出现删 key 空窗；
        // 6. 当前也允许手工重建和启动校准。
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

    /**
     * 注册当前兼容保留缓存域契约。
     *
     * <p>这里注册的域并不是当前四层架构演进的主线重点对象，
     * 但系统仍然需要保留对它们的兼容读写能力。</p>
     *
     * <p>这些域通常具备以下特征之一：</p>
     * <p>1. 仍被历史业务逻辑使用；</p>
     * <p>2. 已有契约和通用写入能力，但不属于当前 manual / boot 默认重点范围；</p>
     * <p>3. 未来可能继续收敛进主线，也可能长期保持兼容模式。</p>
     *
     * @param contracts 用于承接当前已注册缓存域契约的可变列表
     */
    private static void registerLegacyCompatibleContracts(List<CacheDomainContract> contracts) {
        // client_sign：客户签名集合域。
        // 使用 SET 保存某客户绑定的全部签名对象。
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

        // client_template：签名模板集合域。
        // 使用 SET 保存某个签名下的全部模板对象。
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

        // black：黑名单域。
        // 这里声明了两个逻辑 key 模式：
        // 1. black:{mobile}      -> 全局黑名单
        // 2. black:{clientId}:{mobile} -> 客户级黑名单
        // Redis 结构使用 STRING，通常只需要保存一个简单标记值。
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

        // dirty_word：敏感词集合域。
        // 使用固定逻辑 key dirty_word，对应一个字符串 SET。
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

        // transfer：携号转网域。
        // 使用手机号作为逻辑 key 后缀，对应一个 STRING 值，
        // 表示该手机号当前映射的运营商或转网结果。
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
