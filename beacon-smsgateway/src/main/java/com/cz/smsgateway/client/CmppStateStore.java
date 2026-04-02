package com.cz.smsgateway.client;

import com.cz.common.model.StandardReport;
import com.cz.common.model.StandardSubmit;
import com.cz.common.util.JsonUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * CMPP 中间状态存储门面。
 *
 * <p>用于 `beacon-smsgateway` 将提交后、回执前的关联状态写入 `beacon-cache`，
 * 避免仅依赖本地进程内缓存导致重启后无法恢复。</p>
 */
@Component
public class CmppStateStore {

    private static final String SUBMIT_KEY_PREFIX = "cmpp:submit:";
    private static final String DELIVER_KEY_PREFIX = "cmpp:deliver:";

    private final BeaconCacheClient cacheClient;

    @Value("${cmpp.state.submit-ttl-seconds:600}")
    private Long submitTtlSeconds;

    @Value("${cmpp.state.deliver-ttl-seconds:86400}")
    private Long deliverTtlSeconds;

    public CmppStateStore(BeaconCacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    /**
     * 保存待 SubmitResp 关联的短信提交上下文。
     */
    public void saveSubmit(int sequenceId, StandardSubmit submit) {
        if (submit == null) {
            return;
        }
        cacheClient.setString(submitKey(sequenceId), JsonUtil.toJson(submit), submitTtlSeconds);
    }

    /**
     * 原子取走待 SubmitResp 关联的短信提交上下文。
     */
    public StandardSubmit takeSubmit(int sequenceId) {
        return JsonUtil.fromJson(cacheClient.popString(submitKey(sequenceId)), StandardSubmit.class);
    }

    /**
     * 保存待最终状态报告关联的短信回执上下文。
     */
    public void saveDeliver(String msgId, StandardReport report) {
        if (!StringUtils.hasText(msgId) || report == null) {
            return;
        }
        cacheClient.setString(deliverKey(msgId), JsonUtil.toJson(report), deliverTtlSeconds);
    }

    /**
     * 原子取走待最终状态报告关联的短信回执上下文。
     */
    public StandardReport takeDeliver(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            return null;
        }
        return JsonUtil.fromJson(cacheClient.popString(deliverKey(msgId)), StandardReport.class);
    }

    private String submitKey(int sequenceId) {
        return SUBMIT_KEY_PREFIX + sequenceId;
    }

    private String deliverKey(String msgId) {
        return DELIVER_KEY_PREFIX + msgId;
    }
}
