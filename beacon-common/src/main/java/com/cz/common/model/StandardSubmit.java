package com.cz.common.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 短信发送主链路中的统一提交对象。
 *
 * <p>该对象贯穿 API、策略、网关等核心模块，用于承载一条短信从受理到下发前的公共字段。</p>
 *
 * @author cz
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StandardSubmit implements Serializable {

    /**
     * 针对当前短信的唯一标识
     */
    private Long sequenceId;

    /**
     * 客户端ID
     */
    private Long clientId;

    /** 客户 IP 白名单快照。 */
    private List<String> ip;

    /** 客户业务侧请求 ID。 */
    private String uid;

    /**
     * 目标手机号
     */
    private String mobile;

    /**
     * 短信内容的签名
     */
    private String sign;

    /**
     * 短信内容
     */
    private String text;

    /**
     * 短信的发送时间
     */
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime sendTime;

    /** 当前短信的费用，单位：厘。 */
    private Long fee;

    /**
     * 目标手机号的运营商
     */
    private Integer operatorId;


    /** 目标手机号的归属地区号。 */
    private Integer areaCode;

    /** 目标手机号的归属地。 */
    private String area;

    /** 通道下发时使用的源号码。 */
    private String srcNumber;

    /** 选定的通道 ID。 */
    private Long channelId;

    /** 短信发送状态，0-等待，1-成功，2-失败。 */
    private int reportState;

    /** 短信发送失败时的错误信息。 */
    private String errorMsg;

    /** 真实请求 IP。 */
    private String realIp;

    /** 客户端请求携带的 apiKey。 */
    private String apiKey;

    /** 短信类型，0-验证码，1-通知，2-营销。 */
    private int state;

    /**
     * 短信签名ID
     */
    private Long signId;

    /** 是否为携号转网场景。 */
    private Boolean isTransfer = false;

    /** 一小时限流规则使用的时间戳。 */
    private Long oneHourLimitMilli;
}
