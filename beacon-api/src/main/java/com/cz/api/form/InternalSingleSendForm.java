package com.cz.api.form;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Internal SMS send request used by trusted services.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InternalSingleSendForm {

    @NotBlank(message = "apikey can not be blank")
    private String apikey;

    @NotBlank(message = "mobile can not be blank")
    private String mobile;

    @NotBlank(message = "text can not be blank")
    private String text;

    private String uid;

    @Range(min = 0, max = 2, message = "state must be between 0 and 2")
    @NotNull(message = "state can not be null")
    private Integer state;

    /**
     * Optional source IP for downstream processing.
     */
    private String realIp;
}
