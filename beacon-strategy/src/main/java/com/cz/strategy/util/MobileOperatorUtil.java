package com.cz.strategy.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class MobileOperatorUtil {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final String url1 = "https://cx.shouji.360.cn/phonearea.php?number=";

    private final String CODE = "code";
    private final String DATA = "data";
    private final String PROVINCE = "province";
    private final String CITY = "city";
    private final String SP = "sp";
    private final String SPACE = " ";
    private final String SEPARATE = ",";

    /**
     * 获取手机号信息
     * @param mobile 手机号前7位即可
     * @return
     */
    public String getMobileInfoBy360(String mobile) {
        String url = url1;
        //1、发送请求获取信息
        String mobileInfoJSON = restTemplate.getForObject(url + mobile, String.class);
        // {"code":0, "data":{"province":"\u4e91\u5357", "city":"\u6606\u660e", "sp":"\u79fb\u52a8"}}
        //2、解析JSON
        Map map = null;
        try {
            map = objectMapper.readValue(mobileInfoJSON, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        Integer code = (Integer) map.get(CODE);
        if(code != 0) {
            return null;
        }
        Map<String, String> areaAndOperator = (Map<String, String>) map.get(DATA);
        String province = areaAndOperator.get(PROVINCE);
        String city = areaAndOperator.get(CITY);
        String sp = areaAndOperator.get(SP);
        //3、封装为   省 市,运营商    的格式返回
        return province + SPACE + city + SEPARATE + sp;
    }
}
