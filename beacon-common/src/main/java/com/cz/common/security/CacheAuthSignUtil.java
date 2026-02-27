package com.cz.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;

public final class CacheAuthSignUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private CacheAuthSignUtil() {
    }

    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("failed to sign cache auth payload", e);
        }
    }

    public static String buildPayload(String caller, String timestamp, String method, String path) {
        String safeCaller = caller == null ? "" : caller.trim();
        String safeTimestamp = timestamp == null ? "" : timestamp.trim();
        String safeMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        String safePath = normalizePath(path);
        return safeCaller + "\n" + safeTimestamp + "\n" + safeMethod + "\n" + safePath;
    }

    public static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        String normalized = path.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >>> 4) & 0x0F, 16));
            builder.append(Character.forDigit(b & 0x0F, 16));
        }
        return builder.toString();
    }
}
