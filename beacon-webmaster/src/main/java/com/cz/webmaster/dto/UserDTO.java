package com.cz.webmaster.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author cz
 * @description
 */
@Data
public class UserDTO {

    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String captcha;

    private Boolean rememberMe = false;


}
