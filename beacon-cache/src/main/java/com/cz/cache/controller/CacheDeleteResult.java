package com.cz.cache.controller;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存删除接口统一返回模型。
 * <p>
 * 字段约定：
 * <p>
 * 1. attemptedCount：本次尝试删除的 key 数（去重后的有效逻辑 key）；<br>
 * 2. successCount：删除动作执行成功次数（无异常）；<br>
 * 3. deletedCount：Redis 实际删除到的 key 数；<br>
 * 4. failedKeys：删除失败或非法入参 key 列表；<br>
 * 5. namespace：本次删除生效的命名空间前缀。
 */
public class CacheDeleteResult {

    private int attemptedCount;
    private long successCount;
    private long deletedCount;
    private List<String> failedKeys = new ArrayList<>();
    private String namespace;

    public int getAttemptedCount() {
        return attemptedCount;
    }

    public void setAttemptedCount(int attemptedCount) {
        this.attemptedCount = attemptedCount;
    }

    public long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(long successCount) {
        this.successCount = successCount;
    }

    public long getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(long deletedCount) {
        this.deletedCount = deletedCount;
    }

    public List<String> getFailedKeys() {
        return failedKeys;
    }

    public void setFailedKeys(List<String> failedKeys) {
        this.failedKeys = failedKeys == null ? new ArrayList<>() : failedKeys;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}

