package com.cz.webmaster.support;

import com.cz.common.enums.ExceptionEnums;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * 缓存同步日志工具。
 * <p>
 * 统一日志字段，确保后续 Runtime/Manual/Boot 三层输出格式一致，便于检索与排障。
 * <p>
 * 统一字段：
 * traceId, domain, entityId, key, operation, result, costMs, errorCode, errorMsg
 */
public final class CacheSyncLogHelper {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_KEY_UPPER = "TraceId";
    private static final String UNKNOWN = "-";

    private CacheSyncLogHelper() {
    }

    /**
     * 记录成功日志。
     */
    public static void info(Logger log,
                            String domain,
                            String entityId,
                            String key,
                            String operation,
                            long costMs) {
        log.info(buildLogLine(
                resolveTraceId(),
                domain,
                entityId,
                key,
                operation,
                "SUCCESS",
                costMs,
                0,
                ""));
    }

    /**
     * 记录失败日志。
     */
    public static void error(Logger log,
                             String domain,
                             String entityId,
                             String key,
                             String operation,
                             long costMs,
                             ExceptionEnums errorEnum,
                             String errorMsg,
                             Throwable throwable) {
        String message = buildLogLine(
                resolveTraceId(),
                domain,
                entityId,
                key,
                operation,
                "FAIL",
                costMs,
                errorEnum == null ? -1 : errorEnum.getCode(),
                errorMsg);
        if (throwable == null) {
            log.error(message);
            return;
        }
        log.error(message, throwable);
    }

    /**
     * 记录告警日志（用于降级、补偿占位等可观测场景）。
     */
    public static void warn(Logger log,
                            String domain,
                            String entityId,
                            String key,
                            String operation,
                            long costMs,
                            ExceptionEnums errorEnum,
                            String warnMsg,
                            Throwable throwable) {
        String message = buildLogLine(
                resolveTraceId(),
                domain,
                entityId,
                key,
                operation,
                "WARN",
                costMs,
                errorEnum == null ? -1 : errorEnum.getCode(),
                warnMsg);
        if (throwable == null) {
            log.warn(message);
            return;
        }
        log.warn(message, throwable);
    }

    /**
     * 构造统一日志内容。
     */
    static String buildLogLine(String traceId,
                               String domain,
                               String entityId,
                               String key,
                               String operation,
                               String result,
                               long costMs,
                               int errorCode,
                               String errorMsg) {
        return "traceId=" + safe(traceId)
                + ",domain=" + safe(domain)
                + ",entityId=" + safe(entityId)
                + ",key=" + safe(key)
                + ",operation=" + safe(operation)
                + ",result=" + safe(result)
                + ",costMs=" + normalizeCostMs(costMs)
                + ",errorCode=" + errorCode
                + ",errorMsg=" + safe(errorMsg);
    }

    private static String resolveTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = MDC.get(TRACE_ID_KEY_UPPER);
        }
        return safe(traceId);
    }

    private static long normalizeCostMs(long costMs) {
        return costMs < 0 ? -1 : costMs;
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UNKNOWN;
        }
        return value.trim();
    }
}
