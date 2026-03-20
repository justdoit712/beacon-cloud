package com.cz.webmaster.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存重建结果报告模型。
 *
 * <p>用于描述单域或批量缓存重建的执行结果，包含触发信息、执行时间、
 * 处理统计、失败明细和子报告列表。</p>
 */
public class CacheRebuildReport {

    /**
     * 当前请求链路标识。
     */
    private String traceId;

    /**
     * 触发来源，例如 {@code MANUAL}。
     */
    private String trigger;

    /**
     * 当前报告对应的缓存域编码；批量场景下可为 {@code ALL}。
     */
    private String domain;

    /**
     * 执行开始时间戳（毫秒）。
     */
    private Long startAt;

    /**
     * 执行结束时间戳（毫秒）。
     */
    private Long endAt;

    /**
     * 尝试处理的 key 数量。
     */
    private int attemptedKeys;

    /**
     * 处理成功数量。
     */
    private int successCount;

    /**
     * 处理失败数量。
     */
    private int failCount;

    /**
     * 处理失败的 key 列表。
     */
    private List<String> failedKeys = new ArrayList<>();

    /**
     * 是否发生脏标记补跑。
     */
    private boolean dirtyReplay;

    /**
     * 当前操作人 id；未知时允许为 {@code null}。
     */
    private Long operator;

    /**
     * 重建执行状态，例如 {@code SKELETON}、{@code SKIPPED}。
     */
    private String status;

    /**
     * 结果补充说明。
     */
    private String message;

    /**
     * 批量重建场景下的子报告列表。
     */
    private List<CacheRebuildReport> reports = new ArrayList<>();

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Long getStartAt() {
        return startAt;
    }

    public void setStartAt(Long startAt) {
        this.startAt = startAt;
    }

    public Long getEndAt() {
        return endAt;
    }

    public void setEndAt(Long endAt) {
        this.endAt = endAt;
    }

    public int getAttemptedKeys() {
        return attemptedKeys;
    }

    public void setAttemptedKeys(int attemptedKeys) {
        this.attemptedKeys = attemptedKeys;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public List<String> getFailedKeys() {
        return failedKeys;
    }

    public void setFailedKeys(List<String> failedKeys) {
        this.failedKeys = failedKeys == null ? new ArrayList<>() : failedKeys;
    }

    public boolean isDirtyReplay() {
        return dirtyReplay;
    }

    public void setDirtyReplay(boolean dirtyReplay) {
        this.dirtyReplay = dirtyReplay;
    }

    public Long getOperator() {
        return operator;
    }

    public void setOperator(Long operator) {
        this.operator = operator;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<CacheRebuildReport> getReports() {
        return reports;
    }

    public void setReports(List<CacheRebuildReport> reports) {
        this.reports = reports == null ? new ArrayList<>() : reports;
    }
}
