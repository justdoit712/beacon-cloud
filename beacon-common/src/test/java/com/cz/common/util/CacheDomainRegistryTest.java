package com.cz.common.util;

import com.cz.common.cache.meta.CacheDomainContract;
import com.cz.common.cache.meta.CacheDomainRegistry;
import com.cz.common.cache.meta.CacheRedisType;
import com.cz.common.cache.meta.CacheSourceOfTruth;
import com.cz.common.cache.policy.CacheDeletePolicy;
import com.cz.common.cache.policy.CacheRebuildPolicy;
import com.cz.common.cache.policy.CacheWritePolicy;
import com.cz.common.constant.CacheKeyConstants;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link CacheDomainRegistry} 的聚焦测试。
 */
public class CacheDomainRegistryTest {

    /**
     * 验证当前主线域都已经在注册表中可查询。
     */
    @Test
    public void shouldContainFocusedDomains() {
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_BUSINESS));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_SIGN));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_CHANNEL));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CHANNEL));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_BALANCE));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.TRANSFER));
    }

    @Test
    public void shouldExposeCurrentMainlineDomains() {
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_SIGN,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE,
                CacheDomainRegistry.TRANSFER
        ));

        Assert.assertEquals(expected, CacheDomainRegistry.currentMainlineDomainCodes());
        Assert.assertTrue(CacheDomainRegistry.isCurrentMainlineDomain(CacheDomainRegistry.CLIENT_BALANCE));
        Assert.assertTrue(CacheDomainRegistry.isCurrentMainlineDomain(CacheDomainRegistry.TRANSFER));
    }

    /**
     * 验证 主线域的 Redis 结构契约没有漂移。
     *
     * <p>这一步相当于在守住“域规则定义”里的基础字段：
     * 哪些是 HASH，哪些是 SET。</p>
     */
    @Test
    public void focusedDomainsShouldUseExpectedRedisTypes() {
        Assert.assertEquals(CacheRedisType.HASH,
                CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_BUSINESS).getRedisType());
        Assert.assertEquals(CacheRedisType.SET,
                CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_SIGN).getRedisType());
        Assert.assertEquals(CacheRedisType.SET,
                CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_CHANNEL).getRedisType());
        Assert.assertEquals(CacheRedisType.HASH,
                CacheDomainRegistry.require(CacheDomainRegistry.CHANNEL).getRedisType());
        Assert.assertEquals(CacheRedisType.HASH,
                CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_BALANCE).getRedisType());
    }

    @Test
    public void shouldRestrictCurrentManualRebuildDomains() {
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_SIGN,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE,
                CacheDomainRegistry.TRANSFER
        ));

        Assert.assertEquals(expected, CacheDomainRegistry.currentManualRebuildDomainCodes());
        Assert.assertTrue(CacheDomainRegistry.isCurrentManualRebuildDomain(CacheDomainRegistry.CLIENT_BALANCE));
        Assert.assertTrue(CacheDomainRegistry.isCurrentManualRebuildDomain(CacheDomainRegistry.TRANSFER));
    }

    /**
     * 验证余额域仍保持第一层冻结的关键口径：
     * MySQL 为真源，Redis 为 HASH 镜像，且写策略为“原子更新后刷新缓存”。
     */
    @Test
    public void shouldRestrictCurrentBootReconcileDomains() {
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_SIGN,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE,
                CacheDomainRegistry.TRANSFER
        ));

        Assert.assertEquals(expected, CacheDomainRegistry.currentBootReconcileDomainCodes());
        Assert.assertTrue(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_BUSINESS));
        Assert.assertTrue(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_SIGN));
        Assert.assertTrue(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_BALANCE));
        Assert.assertTrue(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.TRANSFER));
        Assert.assertFalse(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_TEMPLATE));
    }

    @Test
    public void clientBalanceShouldUseMysqlAsSourceOfTruth() {
        CacheDomainContract contract = CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_BALANCE);
        Assert.assertEquals(CacheSourceOfTruth.MYSQL, contract.getSourceOfTruth());
        Assert.assertEquals(CacheRedisType.HASH, contract.getRedisType());
        Assert.assertTrue(contract.isBootRebuildEnabled());
        Assert.assertEquals(CacheWritePolicy.MYSQL_ATOMIC_UPDATE_THEN_REFRESH, contract.getWritePolicy());
    }

    @Test
    public void clientSignShouldUseMainlineSetContract() {
        CacheDomainContract contract = CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_SIGN);
        Assert.assertEquals(CacheSourceOfTruth.MYSQL, contract.getSourceOfTruth());
        Assert.assertEquals(CacheRedisType.SET, contract.getRedisType());
        Assert.assertEquals(
                Arrays.asList(CacheKeyConstants.CLIENT_SIGN + "{clientId}"),
                contract.getLogicalKeyPatterns()
        );
        Assert.assertEquals(CacheWritePolicy.DELETE_AND_REBUILD, contract.getWritePolicy());
        Assert.assertEquals(CacheDeletePolicy.DELETE_KEY, contract.getDeletePolicy());
        Assert.assertEquals(CacheRebuildPolicy.FULL_REBUILD, contract.getRebuildPolicy());
        Assert.assertTrue(contract.isBootRebuildEnabled());
    }

    @Test
    public void transferShouldUseMainlineStringContract() {
        CacheDomainContract contract = CacheDomainRegistry.require(CacheDomainRegistry.TRANSFER);
        Assert.assertEquals(CacheSourceOfTruth.MYSQL, contract.getSourceOfTruth());
        Assert.assertEquals(CacheRedisType.STRING, contract.getRedisType());
        Assert.assertEquals(
                Arrays.asList(CacheKeyConstants.TRANSFER + "{mobile}"),
                contract.getLogicalKeyPatterns()
        );
        Assert.assertEquals(CacheWritePolicy.WRITE_THROUGH, contract.getWritePolicy());
        Assert.assertEquals(CacheDeletePolicy.DELETE_KEY, contract.getDeletePolicy());
        Assert.assertEquals(CacheRebuildPolicy.FULL_REBUILD, contract.getRebuildPolicy());
        Assert.assertTrue(contract.isBootRebuildEnabled());
    }
}
