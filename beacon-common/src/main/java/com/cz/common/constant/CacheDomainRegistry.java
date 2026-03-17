package com.cz.common.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存域契约注册表。
 *
 * <p>职责只有两项：</p>
 * <p>1. 注册系统已知的缓存域契约；</p>
 * <p>2. 提供统一的查询入口。</p>
 *
 * <p>该类不负责实际读写 Redis，也不负责业务装配逻辑。</p>
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
    /** 当前允许 ALL 展开的手工重建域集合。 */
    private static final Set<String> CURRENT_MANUAL_REBUILD_DOMAIN_CODES;

    static {
        List<CacheDomainContract> contracts = new ArrayList<>();

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

        contracts.add(new CacheDomainContract(
                CLIENT_BALANCE,
                Collections.singletonList(CacheKeyConstants.CLIENT_BALANCE + "{clientId}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                CacheWritePolicy.MYSQL_ATOMIC_UPDATE_THEN_REFRESH,
                CacheDeletePolicy.OVERWRITE_ONLY,
                CacheRebuildPolicy.FULL_REBUILD_SKIP_BOOT,
                "beacon-webmaster",
                false
        ));

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
        CURRENT_MANUAL_REBUILD_DOMAIN_CODES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
                CLIENT_BUSINESS,
                CLIENT_CHANNEL,
                CHANNEL
        )));
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
     * 返回当前允许由 ALL 展开的手工重建域集合。
     *
     * <p>当前只包含已纳入主线且允许进入第三层默认范围的域。</p>
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
}
