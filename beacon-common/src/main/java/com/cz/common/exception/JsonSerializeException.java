package com.cz.common.exception;

/**
 * JSON 序列化失败时抛出的运行时异常。
 *
 * <p>用于和 IO、HTTP、MQ 等下游调用失败区分开，便于调用方按场景决定
 * 是中断处理、重试还是仅记录告警。</p>
 */
public class JsonSerializeException extends RuntimeException {

    public JsonSerializeException(String message, Throwable cause) {
        super(message, cause);
    }
}
