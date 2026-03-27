package com.cz.webmaster.support;

import com.cz.common.constant.CacheKeyConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 缓存 Key 统一构建器（逻辑 Key 层）。
 * <p>
 * 职责：
 * <p>
 * 1. 统一生成第一层目标域的逻辑 key；<br>
 * 2. 替代业务代码硬编码拼接，降低 key 漂移风险；<br>
 * 3. 对关键入参做基础校验，尽早暴露非法调用。
 * <p>
 * 注意：
 * <p>
 * 1. 本类返回的是“逻辑 key”，不包含 namespace 前缀；<br>
 * 2. 物理前缀追加由 beacon-cache 侧统一处理（NamespaceKeyResolver）。
 */
@Component
    public class CacheKeyBuilder {

    /**
     * 构建客户业务配置 key。
     * <p>
     * 示例：client_business:{apikey}
     */
    public String clientBusinessByApiKey(String apiKey) {
        return CacheKeyConstants.CLIENT_BUSINESS + requireText(apiKey, "apiKey");
    }

    /**
     * 构建客户余额 key。
     * <p>
     * 示例：client_balance:{clientId}
     */
    public String clientBalanceByClientId(Long clientId) {
        return CacheKeyConstants.CLIENT_BALANCE + requirePositiveId(clientId, "clientId");
    }

    /**
     * 构建客户签名集合 key。
     * <p>
     * 示例：client_sign:{clientId}
     */
    public String clientSignByClientId(Long clientId) {
        return CacheKeyConstants.CLIENT_SIGN + requirePositiveId(clientId, "clientId");
    }

    /**
     * 构建签名模板集合 key。
     * <p>
     * 示例：client_template:{signId}
     */
    public String clientTemplateBySignId(Long signId) {
        return CacheKeyConstants.CLIENT_TEMPLATE + requirePositiveId(signId, "signId");
    }

    /**
     * 构建客户通道绑定集合 key。
     * <p>
     * 示例：client_channel:{clientId}
     */
    public String clientChannelByClientId(Long clientId) {
        return CacheKeyConstants.CLIENT_CHANNEL + requirePositiveId(clientId, "clientId");
    }

    /**
     * 构建通道详情 key。
     * <p>
     * 示例：channel:{id}
     */
    public String channelById(Long id) {
        return CacheKeyConstants.CHANNEL + requirePositiveId(id, "id");
    }

    /**
     * 构建全局黑名单 key。
     * <p>
     * 示例：black:{mobile}
     */
    public String blackGlobal(String mobile) {
        return CacheKeyConstants.BLACK + requireText(mobile, "mobile");
    }

    /**
     * 构建客户级黑名单 key。
     * <p>
     * 示例：black:{clientId}:{mobile}
     */
    public String blackClient(Long clientId, String mobile) {
        return CacheKeyConstants.BLACK
                + requirePositiveId(clientId, "clientId")
                + CacheKeyConstants.SEPARATE
                + requireText(mobile, "mobile");
    }

    /**
     * 构建敏感词集合 key。
     * <p>
     * 示例：dirty_word
     */
    public String dirtyWord() {
        return CacheKeyConstants.DIRTY_WORD;
    }

    /**
     * 构建携号转网 key。
     * <p>
     * 示例：transfer:{mobile}
     */
    public String transfer(String mobile) {
        return CacheKeyConstants.TRANSFER + requireText(mobile, "mobile");
    }

    private static String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static long requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return id;
    }
}
