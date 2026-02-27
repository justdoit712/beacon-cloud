package com.cz.common.util;

import com.cz.common.enums.ExceptionEnums;
import com.cz.common.exception.ApiException;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

public class SnowFlakeUtilTest {

    @Test
    public void initShouldRejectNegativeMachineId() throws Exception {
        SnowFlakeUtil util = new SnowFlakeUtil();
        setLongField(util, "machineId", -1L);
        setLongField(util, "serviceId", 0L);

        try {
            util.init();
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertEquals(ExceptionEnums.SNOWFLAKE_OUT_OF_RANGE.getCode(), ex.getCode());
        }
    }

    @Test
    public void nextIdShouldBeUniqueAndIncreasing() throws Exception {
        SnowFlakeUtil util = new SnowFlakeUtil();
        setLongField(util, "machineId", 0L);
        setLongField(util, "serviceId", 0L);
        util.init();

        Set<Long> ids = new HashSet<>();
        long prev = util.nextId();
        ids.add(prev);

        for (int i = 0; i < 1000; i++) {
            long current = util.nextId();
            Assert.assertTrue(current > prev);
            Assert.assertTrue(ids.add(current));
            prev = current;
        }
    }

    @Test
    public void nextIdShouldThrowWhenClockMovesBackward() throws Exception {
        SnowFlakeUtil util = new SnowFlakeUtil();
        setLongField(util, "machineId", 0L);
        setLongField(util, "serviceId", 0L);
        util.init();
        setLongField(util, "lastTimestamp", System.currentTimeMillis() + 10_000L);

        try {
            util.nextId();
            Assert.fail("expected ApiException");
        } catch (ApiException ex) {
            Assert.assertEquals(ExceptionEnums.SNOWFLAKE_TIME_BACK.getCode(), ex.getCode());
        }
    }

    private static void setLongField(Object target, String fieldName, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }
}
