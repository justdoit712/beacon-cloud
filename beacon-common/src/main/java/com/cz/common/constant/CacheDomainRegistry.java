package com.cz.common.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存域契约中央注册表。
 * <p>
 * 该注册表承担两类职责：
 * 1) 定义所有受管控缓存域的统一契约；
 * 2) 提供按域查询、校验、枚举能力，供同步服务、重建服务复用。
 * <p>
 * 注意：本类只描述“规则”，不做任何 Redis 或 DB 的读写动作。
 */
public final class CacheDomainRegistry {

    /** 工具类禁止实例化。 */
    private CacheDomainRegistry() {
    }

    // =========================
    // 域编码常量
    // =========================
    /** 客户业务配置域。 */
    public static final String CLIENT_BUSINESS = "client_business";
    /** 客户签名域。 */
    public static final String CLIENT_SIGN = "client_sign";
    /** 客户模板域。 */
    public static final String CLIENT_TEMPLATE = "client_template";
    /** 客户与通道绑定关系域。 */
    public static final String CLIENT_CHANNEL = "client_channel";
    /** 通道信息域。 */
    public static final String CHANNEL = "channel";
    /** 黑名单域（全局/客户级）。 */
    public static final String BLACK = "black";
    /** 敏感词域。 */
    public static final String DIRTY_WORD = "dirty_word";
    /** 携号转网域。 */
    public static final String TRANSFER = "transfer";
    /** 客户余额域。 */
    public static final String CLIENT_BALANCE = "client_balance";

    // =========================
    // 写入策略常量
    // =========================
    /** 写穿策略：业务写入后直接覆盖缓存。 */
    public static final String WRITE_THROUGH = "WRITE_THROUGH";
    /** 删后重建策略：先删旧 key，再基于真源重建。 */
    public static final String DELETE_AND_REBUILD = "DELETE_AND_REBUILD";
    /** MySQL 原子更新后刷新缓存（余额域专用）。 */
    public static final String MYSQL_ATOMIC_UPDATE_THEN_REFRESH = "MYSQL_ATOMIC_UPDATE_THEN_REFRESH";

    // =========================
    // 删除策略常量
    // =========================
    /** 删除 key。 */
    public static final String DELETE_KEY = "DELETE_KEY";
    /** 只覆盖不删除（常用于不建议直接删 key 的域）。 */
    public static final String OVERWRITE_ONLY = "OVERWRITE_ONLY";

    // =========================
    // 重建策略常量
    // =========================
    /** 允许全量重建（手工/启动均可按开关执行）。 */
    public static final String FULL_REBUILD = "FULL_REBUILD";
    /** 允许全量重建，但启动阶段默认跳过。 */
    public static final String FULL_REBUILD_SKIP_BOOT = "FULL_REBUILD_SKIP_BOOT";

    /** 契约列表（保持注册顺序）。 */
    private static final List<CacheDomainContract> CONTRACTS;
    /** 域编码 -> 契约索引（快速查询）。 */
    private static final Map<String, CacheDomainContract> CONTRACT_MAP;

    static {
        // 先按顺序构建列表，再统一转不可变集合，避免运行时被篡改。
        List<CacheDomainContract> contracts = new ArrayList<>();

        // client_business: 客户基础业务配置，Hash，MySQL 为主，写穿更新。
        contracts.add(new CacheDomainContract(
                CLIENT_BUSINESS,
                Collections.singletonList(CacheConstant.CLIENT_BUSINESS + "{apikey}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                WRITE_THROUGH,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // client_sign: 客户签名集合，Set，推荐删后重建以防脏成员。
        contracts.add(new CacheDomainContract(
                CLIENT_SIGN,
                Collections.singletonList(CacheConstant.CLIENT_SIGN + "{clientId}"),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                DELETE_AND_REBUILD,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // client_template: 签名模板集合，Set，推荐删后重建。
        contracts.add(new CacheDomainContract(
                CLIENT_TEMPLATE,
                Collections.singletonList(CacheConstant.CLIENT_TEMPLATE + "{signId}"),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                DELETE_AND_REBUILD,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // client_channel: 客户通道绑定集合，Set，推荐删后重建。
        contracts.add(new CacheDomainContract(
                CLIENT_CHANNEL,
                Collections.singletonList(CacheConstant.CLIENT_CHANNEL + "{clientId}"),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                DELETE_AND_REBUILD,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // channel: 通道详情，Hash，写穿更新。
        contracts.add(new CacheDomainContract(
                CHANNEL,
                Collections.singletonList(CacheConstant.CHANNEL + "{id}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                WRITE_THROUGH,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // black: 黑名单，包含全局和客户级两类 key 模板，String，写穿更新。
        contracts.add(new CacheDomainContract(
                BLACK,
                Arrays.asList(
                        CacheConstant.BLACK + "{mobile}",
                        CacheConstant.BLACK + "{clientId}" + CacheConstant.SEPARATE + "{mobile}"
                ),
                CacheRedisType.STRING,
                CacheSourceOfTruth.MYSQL,
                WRITE_THROUGH,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // dirty_word: 敏感词集合，Set，删后重建。
        contracts.add(new CacheDomainContract(
                DIRTY_WORD,
                Collections.singletonList(CacheConstant.DIRTY_WORD),
                CacheRedisType.SET,
                CacheSourceOfTruth.MYSQL,
                DELETE_AND_REBUILD,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // transfer: 携号转网映射，String，写穿更新。
        contracts.add(new CacheDomainContract(
                TRANSFER,
                Collections.singletonList(CacheConstant.TRANSFER + "{mobile}"),
                CacheRedisType.STRING,
                CacheSourceOfTruth.MYSQL,
                WRITE_THROUGH,
                DELETE_KEY,
                FULL_REBUILD,
                "beacon-webmaster",
                true
        ));

        // client_balance: 余额域，MySQL 为主口径，启动阶段默认不自动重建。
        contracts.add(new CacheDomainContract(
                CLIENT_BALANCE,
                Collections.singletonList(CacheConstant.CLIENT_BALANCE + "{clientId}"),
                CacheRedisType.HASH,
                CacheSourceOfTruth.MYSQL,
                MYSQL_ATOMIC_UPDATE_THEN_REFRESH,
                OVERWRITE_ONLY,
                FULL_REBUILD_SKIP_BOOT,
                "beacon-webmaster",
                false
        ));

        // 构建索引并校验域编码唯一性，防止重复注册覆盖。
        Map<String, CacheDomainContract> index = new LinkedHashMap<>();
        for (CacheDomainContract contract : contracts) {
            CacheDomainContract previous = index.put(contract.getDomainCode(), contract);
            if (previous != null) {
                throw new IllegalStateException("duplicate domain contract: " + contract.getDomainCode());
            }
        }

        CONTRACTS = Collections.unmodifiableList(contracts);
        CONTRACT_MAP = Collections.unmodifiableMap(index);
    }

    /** @return 全部契约（只读）。 */
    public static List<CacheDomainContract> list() {
        return CONTRACTS;
    }

    /** @return 全部域编码集合。 */
    public static Set<String> domainCodes() {
        return CONTRACT_MAP.keySet();
    }

    /**
     * 按域编码查询契约。
     *
     * @param domainCode 域编码
     * @return 命中则返回契约，未命中返回 null
     */
    public static CacheDomainContract get(String domainCode) {
        return CONTRACT_MAP.get(domainCode);
    }

    /**
     * 按域编码查询契约（强校验版本）。
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
     * 判断域编码是否已注册。
     *
     * @param domainCode 域编码
     * @return true 已注册，false 未注册
     */
    public static boolean contains(String domainCode) {
        return CONTRACT_MAP.containsKey(domainCode);
    }
}
