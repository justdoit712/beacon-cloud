package com.cz.common.util;

import com.cz.common.model.StandardReport;
import org.junit.Assert;
import org.junit.Test;

public class CMPPDeliverMapUtilTest {

    @Test
    public void shouldPutGetAndRemoveReportByMsgId() {
        String msgId = "msg-001";
        StandardReport report = new StandardReport();
        report.setUid("uid-1");

        CMPPDeliverMapUtil.put(msgId, report);

        Assert.assertSame(report, CMPPDeliverMapUtil.get(msgId));
        Assert.assertSame(report, CMPPDeliverMapUtil.remove(msgId));
        Assert.assertNull(CMPPDeliverMapUtil.get(msgId));
    }

    @Test
    public void shouldHandleNullMsgIdGracefully() {
        StandardReport report = new StandardReport();
        report.setUid("uid-null");

        CMPPDeliverMapUtil.put(null, report);
        Assert.assertNull(CMPPDeliverMapUtil.get(null));
        Assert.assertNull(CMPPDeliverMapUtil.remove(null));
    }
}
