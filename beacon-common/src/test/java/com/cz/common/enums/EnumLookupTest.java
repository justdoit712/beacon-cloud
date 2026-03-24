package com.cz.common.enums;

import org.junit.Assert;
import org.junit.Test;

public class EnumLookupTest {

    @Test
    public void mobileOperatorEnumShouldResolveOperatorIdByName() {
        Assert.assertEquals(Integer.valueOf(1), MobileOperatorEnum.operatorIdByName("移动"));
        Assert.assertNull(MobileOperatorEnum.operatorIdByName("不存在"));
    }

    @Test
    public void cmpp2ResultEnumsShouldResolveMessageByResult() {
        Assert.assertEquals("正确", CMPP2ResultEnums.messageOf(0));
        Assert.assertNull(CMPP2ResultEnums.messageOf(99));
    }

    @Test
    public void cmpp2DeliverEnumsShouldResolveDescriptionByStat() {
        Assert.assertEquals("Message is delivered to destination", CMPP2DeliverEnums.descriptionOf("DELIVRD"));
        Assert.assertNull(CMPP2DeliverEnums.descriptionOf("MISSING"));
    }
}
