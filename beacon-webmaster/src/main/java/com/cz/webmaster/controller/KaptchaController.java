package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * 验证码接口
 */
@Controller
@Slf4j
@RequestMapping("/sys/auth")
public class KaptchaController {

    private static final String JPG = "jpg";

    private final DefaultKaptcha kaptcha;

    public KaptchaController(DefaultKaptcha kaptcha) {
        this.kaptcha = kaptcha;
    }

    @GetMapping("/captcha.jpg")
    public void captcha(HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store, no-cache");
        resp.setContentType("image/jpg");

        String text = kaptcha.createText();
        SecurityUtils.getSubject().getSession().setAttribute(WebMasterConstants.KAPTCHA, text);

        BufferedImage image = kaptcha.createImage(text);
        try {
            ServletOutputStream outputStream = resp.getOutputStream();
            ImageIO.write(image, JPG, outputStream);
        } catch (IOException e) {
            log.error("write captcha image failed", e);
        }
    }
}
