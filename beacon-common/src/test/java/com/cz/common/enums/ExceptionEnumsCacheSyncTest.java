package com.cz.common.enums;

import org.junit.Assert;
import org.junit.Test;

/**
 * 缓存同步错误码单元测试。
 */
public class ExceptionEnumsCacheSyncTest {

    @Test
    public void shouldExposeCacheSyncErrorCodes() {
        Assert.assertEquals(Integer.valueOf(-201), ExceptionEnums.CACHE_SYNC_WRITE_FAIL.getCode());
        Assert.assertEquals(Integer.valueOf(-202), ExceptionEnums.CACHE_SYNC_DELETE_FAIL.getCode());
        Assert.assertEquals(Integer.valueOf(-203), ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getCode());
        Assert.assertTrue(ExceptionEnums.CACHE_SYNC_WRITE_FAIL.getMsg().contains("缓存同步"));
        Assert.assertTrue(ExceptionEnums.CACHE_SYNC_DELETE_FAIL.getMsg().contains("缓存同步"));
        Assert.assertTrue(ExceptionEnums.CACHE_SYNC_CONFIG_INVALID.getMsg().contains("配置"));
    }
}

