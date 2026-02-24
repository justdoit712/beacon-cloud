package com.cz.webmaster.controller;

import com.cz.common.constant.WebMasterConstants;
import com.cz.webmaster.client.SearchClient;
import com.cz.webmaster.entity.ClientBusiness;
import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.service.ClientBusinessService;
import com.cz.webmaster.service.SmsRoleService;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 图表相关接口
 */
@RestController
@RequestMapping("/sys/echarts")
public class EchartsController {

    @Autowired
    private SearchClient searchClient;

    @Autowired
    private SmsRoleService roleService;

    @Autowired
    private ClientBusinessService clientBusinessService;

    @GetMapping("/pie")
    public Map<String, Object> pie(@RequestParam Map<String, Object> params) {
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            return buildPieResult(0, 0, 0);
        }

        String clientIDStr = toStr(params.get("clientID"));
        Long clientID = null;
        if (StringUtils.hasText(clientIDStr)) {
            clientID = Long.parseLong(clientIDStr);
        }

        Set<String> roleNames = roleService.getRoleName(smsUser.getId());
        if (roleNames != null && !roleNames.contains(WebMasterConstants.ROOT)) {
            List<ClientBusiness> clients = clientBusinessService.findByUserId(smsUser.getId());
            if (clientID == null) {
                List<Long> list = new ArrayList<>();
                for (ClientBusiness client : clients) {
                    list.add(client.getId());
                }
                params.put("clientID", list);
            } else {
                boolean allow = false;
                for (ClientBusiness client : clients) {
                    if (clientID.equals(client.getId())) {
                        allow = true;
                        break;
                    }
                }
                if (!allow) {
                    return buildPieResult(0, 0, 0);
                }
            }
        }

        Map<String, Integer> stateCount = searchClient.countSmsState(params);

        if (stateCount == null || stateCount.isEmpty()) {
            return buildPieResult(0, 0, 0);
        }

        int waiting = stateCount.getOrDefault("waiting", 0);
        int success = stateCount.getOrDefault("success", 0);
        int fail = stateCount.getOrDefault("fail", 0);
        return buildPieResult(waiting, success, fail);
    }

    private Map<String, Object> buildPieResult(int waiting, int success, int fail) {
        List<String> legendData = new ArrayList<>();
        legendData.add("等待");
        legendData.add("成功");
        legendData.add("失败");

        List<Map<String, Object>> seriesData = new ArrayList<>();
        seriesData.add(item("等待", waiting));
        seriesData.add(item("成功", success));
        seriesData.add(item("失败", fail));

        Map<String, Object> result = new HashMap<>();
        result.put("legendData", legendData);
        result.put("seriesData", seriesData);
        return result;
    }

    private Map<String, Object> item(String name, int value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object rowsObj = data.get("rows");
        if (!(rowsObj instanceof List)) {
            return null;
        }
        return (List<Map<String, Object>>) rowsObj;
    }
}
