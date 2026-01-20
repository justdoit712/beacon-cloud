package com.cz.api.controller;

import com.cz.api.filter.CheckFilterContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author cz
 * @description  测试校验链的效果
 */
@Controller
public class TestController {

    @Autowired
    private CheckFilterContext checkFilterContext;

    @GetMapping("/api/test")
    public void test(){
        System.out.println("====================================");
        checkFilterContext.check(null);
    }
}