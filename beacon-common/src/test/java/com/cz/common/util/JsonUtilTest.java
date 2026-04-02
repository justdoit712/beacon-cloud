package com.cz.common.util;

import com.cz.common.exception.JsonSerializeException;
import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;

public class JsonUtilTest {

    @Test
    public void toJsonShouldSerializeLocalDateTimeAsIsoString() {
        TimePayload payload = new TimePayload();
        payload.time = LocalDateTime.of(2026, 2, 27, 10, 11, 12);

        String json = JsonUtil.toJson(payload);

        Assert.assertTrue(json.contains("\"time\":\"2026-02-27T10:11:12\""));
    }

    @Test
    public void toJsonShouldKeepCauseWhenSerializationFails() {
        try {
            JsonUtil.toJson(new SelfRefPayload());
            Assert.fail("expected JsonSerializeException");
        } catch (JsonSerializeException ex) {
            Assert.assertNotNull(ex.getCause());
        }
    }

    @Test
    public void toMapShouldConvertPayloadWithLocalDateTime() {
        TimePayload payload = new TimePayload();
        payload.time = LocalDateTime.of(2026, 2, 27, 10, 11, 12);

        Map<String, Object> map = JsonUtil.toMap(payload);

        Assert.assertEquals("2026-02-27T10:11:12", String.valueOf(map.get("time")));
    }

    @Test
    public void fromJsonShouldDeserializeLocalDateTime() {
        String json = "{\"time\":\"2026-02-27T10:11:12\"}";

        TimePayload payload = JsonUtil.fromJson(json, TimePayload.class);

        Assert.assertNotNull(payload);
        Assert.assertEquals(LocalDateTime.of(2026, 2, 27, 10, 11, 12), payload.time);
    }

    @Test
    public void fromJsonShouldKeepCauseWhenDeserializationFails() {
        try {
            JsonUtil.fromJson("{bad json}", TimePayload.class);
            Assert.fail("expected JsonSerializeException");
        } catch (JsonSerializeException ex) {
            Assert.assertNotNull(ex.getCause());
        }
    }

    private static final class TimePayload {
        public LocalDateTime time;
    }

    private static final class SelfRefPayload {
        public SelfRefPayload self = this;
    }
}
