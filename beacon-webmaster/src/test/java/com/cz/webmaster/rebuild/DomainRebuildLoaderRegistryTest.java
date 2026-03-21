package com.cz.webmaster.rebuild;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DomainRebuildLoaderRegistryTest {

    @Test
    public void shouldIndexRegisteredLoadersByDomainCode() {
        DomainRebuildLoaderRegistry registry = new DomainRebuildLoaderRegistry(Arrays.asList(
                stubLoader("client_business"),
                stubLoader("channel")
        ));

        Assert.assertEquals(2, registry.list().size());
        Assert.assertTrue(registry.contains("client_business"));
        Assert.assertTrue(registry.contains("CHANNEL"));
        Assert.assertFalse(registry.contains("client_channel"));
    }

    @Test
    public void shouldRejectDuplicateDomainLoaderRegistration() {
        try {
            new DomainRebuildLoaderRegistry(Arrays.asList(
                    stubLoader("channel"),
                    stubLoader("CHANNEL")
            ));
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("duplicate rebuild loader"));
        }
    }

    private DomainRebuildLoader stubLoader(String domainCode) {
        return new DomainRebuildLoader() {
            @Override
            public String domainCode() {
                return domainCode;
            }

            @Override
            public List<Object> loadSnapshot() {
                return Collections.emptyList();
            }
        };
    }
}
