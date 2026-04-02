package com.cz.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cache 字符串写入请求。
 *
 * <p>用于 `beacon-smsgateway` 调用 `beacon-cache` 字符串缓存接口时的请求体。</p>
 *
 * <p>当前主要承载 CMPP 提交/回执关联阶段的中间状态数据，
 * 例如 `StandardSubmit`、`StandardReport` 序列化后的 JSON 字符串及其 TTL。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheStringWriteRequest {

    /**
     * 字符串缓存值，例如 `StandardSubmit` 或 `StandardReport` 的 JSON 内容。
     */
    private String value;

    /**
     * 过期时间（秒）；为空或小于等于 0 时表示不额外设置 TTL。
     */
    private Long ttlSeconds;
}
