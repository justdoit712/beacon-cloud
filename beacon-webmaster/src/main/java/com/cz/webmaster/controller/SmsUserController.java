package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.common.enums.ExceptionEnums;
import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.dto.UserDTO;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.SmsMenuService;
import com.cz.webmaster.service.SmsUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证、登录、用户信息接口
 */
@RestController
@RequestMapping("/sys")
@Slf4j
public class SmsUserController {

    private final String testKaptcha;
    private final SmsMenuService menuService;
    private final SmsUserService userService;

    public SmsUserController(@Value("${system.test-kaptcha:}") String testKaptcha,
                             SmsMenuService menuService,
                             SmsUserService userService) {
        this.testKaptcha = testKaptcha;
        this.menuService = menuService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResultVO<?> login(@RequestBody @Valid UserDTO userDTO, BindingResult bindingResult) {
        if (bindingResult.hasFieldErrors("captcha")) {
            log.info("【认证操作】验证码参数不合法, userDTO={}", userDTO);
            return Result.error(ExceptionEnums.KAPACHA_ERROR);
        }
        if (bindingResult.hasErrors()) {
            log.info("【认证操作】参数不合法, userDTO={}", userDTO);
            return Result.error(ExceptionEnums.PARAMETER_ERROR);
        }

        String realKaptcha = (String) SecurityUtils.getSubject().getSession().getAttribute(WebMasterConstants.KAPTCHA);
        boolean isTestKaptcha = StringUtils.hasText(testKaptcha) && testKaptcha.equals(userDTO.getCaptcha());
        if (!isTestKaptcha && (!StringUtils.hasText(realKaptcha) || !userDTO.getCaptcha().equalsIgnoreCase(realKaptcha))) {
            log.info("【认证操作】验证码错误, input={}, expected={}", userDTO.getCaptcha(), realKaptcha);
            return Result.error(ExceptionEnums.KAPACHA_ERROR);
        }

        UsernamePasswordToken token = new UsernamePasswordToken(userDTO.getUsername(), userDTO.getPassword());
        token.setRememberMe(userDTO.getRememberMe());
        try {
            SecurityUtils.getSubject().login(token);
        } catch (AuthenticationException e) {
            log.info("【认证操作】用户名或密码错误, ex={}", e.getMessage());
            return Result.error(ExceptionEnums.AUTHEN_ERROR);
        }
        return Result.ok();
    }

    @GetMapping("/user/info")
    public ResultVO<?> info() {
        Subject subject = SecurityUtils.getSubject();
        SmsUser smsUser = (SmsUser) subject.getPrincipal();
        if (smsUser == null) {
            log.info("【获取登录用户信息】用户未登录");
            return Result.error(ExceptionEnums.NOT_LOGIN);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("nickname", smsUser.getNickname());
        return Result.ok(data);
    }

    @GetMapping("/menu/user")
    public ResultVO<?> menuUser() {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            log.info("【获取用户菜单信息】用户未登录");
            return Result.error(ExceptionEnums.NOT_LOGIN);
        }

        List<Map<String, Object>> data = menuService.findUserMenu(smsUser.getId());
        if (data == null) {
            log.error("【获取用户菜单信息】查询用户菜单失败, id={}", smsUser.getId());
            return Result.error(ExceptionEnums.USER_MENU_ERROR);
        }
        return Result.ok(data);
    }

    @PostMapping("/user/password")
    public ResultVO<?> updatePassword(@RequestParam("password") String password,
                                      @RequestParam("newPassword") String newPassword) {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            return Result.error(ExceptionEnums.NOT_LOGIN);
        }
        if (!StringUtils.hasText(password) || !StringUtils.hasText(newPassword)) {
            return Result.error(ExceptionEnums.PARAMETER_ERROR);
        }

        boolean success = userService.updatePassword(smsUser.getId(), password, newPassword);
        if (!success) {
            return Result.error("原密码错误或修改失败");
        }
        return Result.ok("修改成功");
    }
}

