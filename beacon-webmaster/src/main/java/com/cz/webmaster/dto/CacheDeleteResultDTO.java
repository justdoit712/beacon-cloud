package com.cz.webmaster.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * beacon-cache 删除接口响应模型。
 */
public class CacheDeleteResultDTO {

    /**
     * 尝试删除的 key 数（去重后的有效逻辑 key）。
     */
    private int attemptedCount;

    /**
     * 删除动作执行成功次数（无异常）。
     */
    private long successCount;

    /**
     * Redis 实际删除到的 key 数。
     */
    private long deletedCount;

    /**
     * 删除失败或非法入参 key。
     */
    private List<String> failedKeys = new ArrayList<>();

    /**
     * 本次操作命名空间。
     */
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

