package com.cz.api.form;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;

/**
 * @author zc
 * @description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SingleSendForm {

    /** 客户的apikey */
    @NotBlank(message = "apikey不能为空")
    private String apikey;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String mobile;

    /** 短信内容 */
    @NotBlank(message = "短信内容不能为空")
    private String text;

    /** 客户业务内的uid */
    private String uid;

    /** 0-验证码短信 1-通知类短信 2-营销类短信 */
    @Range(min = 0, max = 2, message = "短信类型只能是0-2的整数")
    @NonNull
    private Integer state;

}