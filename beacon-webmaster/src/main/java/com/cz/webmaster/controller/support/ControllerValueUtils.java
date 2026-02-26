package com.cz.webmaster.controller.support;

import org.springframework.util.StringUtils;

public final class ControllerValueUtils {

    private ControllerValueUtils() {
    }

    public static String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return parseInt(value, 0);
    }

    public static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }
}
