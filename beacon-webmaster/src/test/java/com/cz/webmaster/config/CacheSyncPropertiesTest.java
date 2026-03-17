package com.cz.webmaster.config;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * CacheSyncProperties 单元测试。
 * <p>
 * 验证第 4 步中最关键的两个行为：
 * 1) namespace 不能为空；
 * 2) namespace 会被规范化为“冒号结尾”。
 */
public class CacheSyncPropertiesTest {

    /**
     * 当 namespace 没有冒号结尾时，应自动补齐。
     */
    @Test
    public void shouldNormalizeNamespaceWithSuffixColon() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz");

        properties.validate();

        Assert.assertEquals("beacon:dev:beacon-cloud:cz:", properties.resolveNamespace());
    }

    /**
     * 当同步总开关开启且 namespace 为空白时，应抛出非法参数异常。
     */
    @Test
    public void shouldRejectBlankNamespaceWhenSyncEnabled() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:dev:beacon-cloud:cz:")
        );
        properties.setEnabled(true);
        properties.getRedis().setNamespace("   ");

        try {
            properties.validate();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("sync.redis.namespace"));
        }
    }

    @Test
    public void shouldRejectBlankCacheNamespaceFullPrefixWhenSyncEnabled() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", " ")
        );
        properties.setEnabled(true);
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz:");

        try {
            properties.validate();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("cache.namespace.fullPrefix"));
        }
    }

    @Test
    public void shouldRejectMismatchedNamespaceWhenSyncEnabled() {
        CacheSyncProperties properties = new CacheSyncProperties(
                new MockEnvironment().withProperty("cache.namespace.fullPrefix", "beacon:test:demo:owner:")
        );
        properties.setEnabled(true);
        properties.getRedis().setNamespace("beacon:dev:beacon-cloud:cz:");

        try {
            properties.validate();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("must match"));
        }
    }
}
