package com.cz.common.util;

import com.cz.common.constant.CacheDomainContract;
import com.cz.common.constant.CacheDomainRegistry;
import com.cz.common.constant.CacheRedisType;
import com.cz.common.constant.CacheSourceOfTruth;
import org.junit.Assert;
import org.junit.Test;

/**
 * CacheDomainRegistry 单元测试。
 * <p>
 * 用于验证两件关键事实：
 * 1) 目标域是否完整注册；
 * 2) 余额域（client_balance）是否按约束配置为 MySQL 主口径。
 */
public class CacheDomainRegistryTest {

    /**
     * 验证首批 9 个域全部在注册表中，且数量与预期一致。
     */
    @Test
    public void shouldContainExpectedDomains() {
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_BUSINESS));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_SIGN));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_TEMPLATE));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_CHANNEL));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CHANNEL));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.BLACK));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.DIRTY_WORD));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.TRANSFER));
        Assert.assertTrue(CacheDomainRegistry.contains(CacheDomainRegistry.CLIENT_BALANCE));
        Assert.assertEquals(9, CacheDomainRegistry.list().size());
    }

    /**
     * 验证余额域契约：
     * 1) 主口径为 MySQL；
     * 2) Redis 结构为 HASH；
     * 3) 启动阶段默认不自动重建；
     * 4) 写策略为“MySQL 原子更新后刷新缓存”。
     */
    @Test
    public void clientBalanceShouldUseMysqlAsSourceOfTruth() {
        CacheDomainContract contract = CacheDomainRegistry.require(CacheDomainRegistry.CLIENT_BALANCE);
        Assert.assertEquals(CacheSourceOfTruth.MYSQL, contract.getSourceOfTruth());
        Assert.assertEquals(CacheRedisType.HASH, contract.getRedisType());
        Assert.assertFalse(contract.isBootRebuildEnabled());
        Assert.assertEquals(CacheDomainRegistry.MYSQL_ATOMIC_UPDATE_THEN_REFRESH, contract.getWritePolicy());
    }
}
