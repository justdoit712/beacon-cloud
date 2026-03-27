package com.cz.common.constant;

/**
 * 缓存逻辑 key 前缀与常用字段常量。
 *
 * @author cz
 */
public interface CacheKeyConstants {
    /** 客户业务配置。 */
    String CLIENT_BUSINESS = "client_business:";
    /** 客户签名集合。 */
    String CLIENT_SIGN = "client_sign:";
    /** 客户签名模板集合。 */
    String CLIENT_TEMPLATE = "client_template:";
    /** 客户余额镜像。 */
    String CLIENT_BALANCE = "client_balance:";

    /** 号段补齐。 */
    String PHASE = "phase:";

    /** 敏感词集合。 */
    String DIRTY_WORD = "dirty_word";

    /** `client_business` hash 中的回调开关字段。 */
    String IS_CALLBACK = "isCallback";

    /** `client_business` hash 中的回调地址字段。 */
    String CALLBACK_URL = "callbackUrl";

    /** 黑名单。 */
    String BLACK = "black:";

    /** 通用分隔符。 */
    String SEPARATE = ":";

    /** 携号转网结果。 */
    String TRANSFER = "transfer:";






    /** 分钟级限流规则。 */
    String LIMIT_MINUTES = "limit:minutes:";

    /** 小时级限流规则。 */
    String LIMIT_HOURS = "limit:hours:";

    /** 天级限流规则。 */
    String LIMIT_DAYS = "limit:days:";

    /** 客户通道绑定集合。 */
    String CLIENT_CHANNEL = "client_channel:";

    /** 通道详情。 */
    String CHANNEL = "channel:";
}
