package com.cz.webmaster.support;

import org.junit.Assert;
import org.junit.Test;

/**
 * CacheKeyBuilder 单元测试。
 * <p>
 * 覆盖第一层要求的 10 个逻辑 key 生成方法，以及关键参数校验行为。
 */
public class CacheKeyBuilderTest {

    private final CacheKeyBuilder keyBuilder = new CacheKeyBuilder();

    @Test
    public void shouldBuildAllRequiredLogicalKeys() {
        Assert.assertEquals("client_business:ak_001", keyBuilder.clientBusinessByApiKey("ak_001"));
        Assert.assertEquals("client_balance:1001", keyBuilder.clientBalanceByClientId(1001L));
        Assert.assertEquals("client_sign:1001", keyBuilder.clientSignByClientId(1001L));
        Assert.assertEquals("client_template:2002", keyBuilder.clientTemplateBySignId(2002L));
        Assert.assertEquals("client_channel:1001", keyBuilder.clientChannelByClientId(1001L));
        Assert.assertEquals("channel:3003", keyBuilder.channelById(3003L));
        Assert.assertEquals("black:13800000000", keyBuilder.blackGlobal("13800000000"));
        Assert.assertEquals("black:1001:13800000000", keyBuilder.blackClient(1001L, "13800000000"));
        Assert.assertEquals("dirty_word", keyBuilder.dirtyWord());
        Assert.assertEquals("transfer:13800000000", keyBuilder.transfer("13800000000"));
    }

    @Test
    public void shouldTrimTextInputWhenBuildKey() {
        Assert.assertEquals("client_business:ak_001", keyBuilder.clientBusinessByApiKey("  ak_001  "));
        Assert.assertEquals("black:13800000000", keyBuilder.blackGlobal(" 13800000000 "));
        Assert.assertEquals("transfer:13800000000", keyBuilder.transfer(" 13800000000 "));
    }

    @Test
    public void shouldRejectBlankOrInvalidInput() {
        assertIllegalArg(() -> keyBuilder.clientBusinessByApiKey("   "), "apiKey");
        assertIllegalArg(() -> keyBuilder.clientBalanceByClientId(0L), "clientId");
        assertIllegalArg(() -> keyBuilder.clientSignByClientId(null), "clientId");
        assertIllegalArg(() -> keyBuilder.clientTemplateBySignId(-1L), "signId");
        assertIllegalArg(() -> keyBuilder.channelById(0L), "id");
        assertIllegalArg(() -> keyBuilder.blackGlobal(""), "mobile");
        assertIllegalArg(() -> keyBuilder.blackClient(1L, " "), "mobile");
        assertIllegalArg(() -> keyBuilder.blackClient(0L, "13800000000"), "clientId");
        assertIllegalArg(() -> keyBuilder.transfer(null), "mobile");
    }

    private static void assertIllegalArg(Runnable runnable, String expectedField) {
        try {
            runnable.run();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains(expectedField));
        }
    }
}

