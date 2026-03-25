package com.cz.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 回执更新与客户回调共用的统一报告对象。
 *
 * @author cz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardReport implements Serializable {

    /** 客户 apiKey，用于查询回调配置等扩展信息。 */
    private String apiKey;

    /** 短信唯一标识。 */
    private Long sequenceId;

    /** 客户 ID。 */
    private Long clientId;


    /** 客户业务侧请求 ID。 */
    private String uid;

    /** 目标手机号。 */
    private String mobile;

    /** 短信发送时间。 */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime sendTime;

    /** 短信发送状态，0-等待，1-成功，2-失败。 */
    private int reportState;

    /** 短信发送失败时的错误信息。 */
    private String errorMsg;

    /** 客户回调开关与回调地址。 */
    private Integer isCallback;
    private String callbackUrl;

    /** 客户回调重试次数。 */
    private Integer resendCount = 0;

    /** 是否为二次更新投递。 */
    private Boolean reUpdate = false;
}
