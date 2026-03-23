package com.cz.common.util;

import com.cz.common.constant.CacheDomainContract;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.constant.CacheRedisType;
import com.cz.common.constant.CacheSourceOfTruth;
import com.cz.common.constant.CacheWritePolicy;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link CacheDomainRegistry} 的聚焦测试。
 *
 * <p>本轮治理只关注 4 个主线域：
 * client_business、client_channel、channel、client_balance。
 * 因此这里的断言也只覆盖这 4 个域，避免把当前不关心的域带进阅读视线。</p>
 */
public class CacheDomainRegistryTest {

    /**
     * 验证当前主线 4 个域都已经在注册表中可查询。
     *
     * <p>这里只证明“本次关注域可用”，不再同时枚举其它非主线域，
     * 这样测试意图会更聚焦。</p>
     */
    @Test
    public void shouldContainFocusedDomains() {
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_BUSINESS));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_CHANNEL));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CHANNEL));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_BALANCE));
    }

    @Test
    public void shouldExposeCurrentMainlineDomains() {
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE
        ));

        Assert.assertEquals(expected, CacheDomainRegistry.currentMainlineDomainCodes());
        Assert.assertTrue(CacheDomainRegistry.isCurrentMainlineDomain(CacheDomainRegistry.CLIENT_BALANCE));
    }

    /**
     * 验证 4 个主线域的 Redis 结构契约没有漂移。
     *
     * <p>这一步相当于在守住“域规则定义”里的基础字段：
     * 哪些是 HASH，哪些是 SET。</p>
     */
    @Test
    public void focusedDomainsShouldUseExpectedRedisTypes() {
        Assert.assertEquals(CacheRedisType.HASH,
                CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_BUSINESS).getRedisType());
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
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE
        ));

        Assert.assertEquals(expected, CacheDomainRegistry.currentManualRebuildDomainCodes());
        Assert.assertTrue(CacheDomainRegistry.isCurrentManualRebuildDomain(CacheDomainRegistry.CLIENT_BALANCE));
    }

    /**
     * 验证余额域仍保持第一层冻结的关键口径：
     * MySQL 为真源，Redis 为 HASH 镜像，且写策略为“原子更新后刷新缓存”。
     */
    @Test
    public void shouldRestrictCurrentBootReconcileDomains() {
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(
                CacheDomainRegistry.CLIENT_BUSINESS,
                CacheDomainRegistry.CLIENT_CHANNEL,
                CacheDomainRegistry.CHANNEL,
                CacheDomainRegistry.CLIENT_BALANCE
        ));

        Assert.assertEquals(expected, CacheDomainRegistry.currentBootReconcileDomainCodes());
        Assert.assertTrue(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_BUSINESS));
        Assert.assertTrue(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_BALANCE));
        Assert.assertFalse(CacheDomainRegistry.isCurrentBootReconcileDomain(CacheDomainRegistry.CLIENT_SIGN));
    }

    @Test
    public void clientBalanceShouldUseMysqlAsSourceOfTruth() {
        CacheDomainContract contract = CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_BALANCE);
        Assert.assertEquals(CacheSourceOfTruth.MYSQL, contract.getSourceOfTruth());
        Assert.assertEquals(CacheRedisType.HASH, contract.getRedisType());
        Assert.assertTrue(contract.isBootRebuildEnabled());
        Assert.assertEquals(CacheWritePolicy.MYSQL_ATOMIC_UPDATE_THEN_REFRESH, contract.getWritePolicy());
    }
}
