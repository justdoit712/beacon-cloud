package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.google.code.kaptcha.impl.DefaultKaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码接口
 */
@Controller
@Slf4j
@RequestMapping("/sys/auth")
public class KaptchaController {

    private static final String JPG = "jpg";

    private final DefaultKaptcha kaptcha;

    // 简单的本地内存验证码存储 (在生产环境中，推荐使用 Redis)
    public static final Map<String, String> CAPTCHA_MAP = new ConcurrentHashMap<>();

    public KaptchaController(DefaultKaptcha kaptcha) {
        this.kaptcha = kaptcha;
    }

    @GetMapping("/captcha.jpg")
    public void captcha(@RequestParam("uuid") String uuid, HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store, no-cache");
        resp.setContentType("image/jpg");

        String text = kaptcha.createText();
        CAPTCHA_MAP.put(uuid, text);

        BufferedImage image = kaptcha.createImage(text);
        try {
            ServletOutputStream outputStream = resp.getOutputStream();
            ImageIO.write(image, JPG, outputStream);
        } catch (IOException e) {
            log.error("write captcha image failed", e);
        }
    }
}
