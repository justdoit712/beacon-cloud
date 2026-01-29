package com.cz.common.model.constant;
/*
* 缓存模块中的各种前缀
* @author cz
* @description
*/
public interface CacheConstant {
    /**
     * 客户信息
     */
    String CLIENT_BUSINESS = "client_business:";
    /**
     * 客户签名
     */
    String CLIENT_SIGN = "client_sign:";
    /**
     * 客户签名的模板
     */
    String CLIENT_TEMPLATE = "client_template:";
    /**
     * 客户的余额
     */
    String CLIENT_BALANCE = "client_balance:";

    /**
     * 号段补全
     */
    String PHASE = "phase:";

    /**
     * 敏感词
     */
    String DIRTY_WORD = "dirty_word";

    /**
     * 回调签名
     */
    String IS_CALLBACK = "is_Callback:";


    /**
     * 回调地址
     */
    String CALLBACK_URL = "callback_url";

    /**
     * 黑名单
     */
    String BLACK = "black:";

    /**
     * 分隔符
     */
    String SEPARATE = ":";
}
