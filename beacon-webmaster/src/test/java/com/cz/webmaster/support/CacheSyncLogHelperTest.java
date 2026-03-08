package com.cz.webmaster.support;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.MDC;

/**
 * CacheSyncLogHelper 单元测试。
 * <p>
 * 验证统一日志字段格式是否完整，确保后续同步日志可直接用于检索与排障。
 */
public class CacheSyncLogHelperTest {

    @Test
    public void shouldBuildStructuredLogLineWithAllFields() {
        MDC.put("traceId", "trace-001");
        try {
            String line = CacheSyncLogHelper.buildLogLine(
                    "trace-001",
                    "client_balance",
                    "1001",
                    "client_balance:1001",
                    "syncUpsert",
                    "SUCCESS",
                    23L,
                    0,
                    ""
            );
            Assert.assertTrue(line.contains("traceId=trace-001"));
            Assert.assertTrue(line.contains("domain=client_balance"));
            Assert.assertTrue(line.contains("entityId=1001"));
            Assert.assertTrue(line.contains("key=client_balance:1001"));
            Assert.assertTrue(line.contains("operation=syncUpsert"));
            Assert.assertTrue(line.contains("result=SUCCESS"));
            Assert.assertTrue(line.contains("costMs=23"));
            Assert.assertTrue(line.contains("errorCode=0"));
            Assert.assertTrue(line.contains("errorMsg=-"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    public void shouldNormalizeBlankValueAndNegativeCost() {
        String line = CacheSyncLogHelper.buildLogLine(
                " ",
                null,
                "",
                "   ",
                "syncDelete",
                "FAIL",
                -3L,
                -202,
                null
        );
        Assert.assertTrue(line.contains("traceId=-"));
        Assert.assertTrue(line.contains("domain=-"));
        Assert.assertTrue(line.contains("entityId=-"));
        Assert.assertTrue(line.contains("key=-"));
        Assert.assertTrue(line.contains("costMs=-1"));
        Assert.assertTrue(line.contains("errorCode=-202"));
        Assert.assertTrue(line.contains("errorMsg=-"));
    }
}

