package com.cz.webmaster.controller.support;

import java.util.HashMap;
import java.util.Map;

public final class StatusResultUtils {

    private StatusResultUtils() {
    }

    public static Map<String, Object> ok(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", true);
        result.put("msg", msg);
        result.put("message", msg);
        return result;
    }

    public static Map<String, Object> fail(String msg) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", false);
        result.put("msg", msg);
        result.put("message", msg);
        return result;
    }
}
