package com.cz.cache.security;

import org.junit.Assert;
import org.junit.Test;

public class CacheNamespacePropertiesTest {

    @Test
    public void shouldUseDefaultFullPrefix() {
        CacheNamespaceProperties properties = new CacheNamespaceProperties();

        Assert.assertEquals("beacon:dev:beacon-cloud:cz:", properties.resolvePrefix());
    }

    @Test
    public void shouldAppendColonWhenFullPrefixHasNoSuffixColon() {
        CacheNamespaceProperties properties = new CacheNamespaceProperties();
        properties.setFullPrefix("beacon:test:demo:owner");

        Assert.assertEquals("beacon:test:demo:owner:", properties.resolvePrefix());
    }

    @Test
    public void shouldReturnEmptyPrefixWhenDisabled() {
        CacheNamespaceProperties properties = new CacheNamespaceProperties();
        properties.setEnabled(false);

        Assert.assertEquals("", properties.resolvePrefix());
    }

    @Test
    public void shouldRejectBlankFullPrefixWhenEnabled() {
        CacheNamespaceProperties properties = new CacheNamespaceProperties();
        properties.setFullPrefix(" ");

        try {
            properties.resolvePrefix();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("cache.namespace.fullPrefix"));
        }
    }
}
