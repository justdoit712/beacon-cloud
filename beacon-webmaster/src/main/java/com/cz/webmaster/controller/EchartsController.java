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
        Map<String, Integer> stateCount = queryStateCountWithPermission(params);
        int waiting = stateCount.getOrDefault("waiting", 0);
        int success = stateCount.getOrDefault("success", 0);
        int fail = stateCount.getOrDefault("fail", 0);
        return buildPieResult(waiting, success, fail);
    }

    @GetMapping("/line")
    public Map<String, Object> line(@RequestParam Map<String, Object> params) {
        Map<String, Integer> stateCount = queryStateCountWithPermission(params);

        List<String> xAxis = new ArrayList<>();
        xAxis.add("绛夊緟");
        xAxis.add("鎴愬姛");
        xAxis.add("澶辫触");

        List<Integer> seriesData = new ArrayList<>();
        seriesData.add(stateCount.getOrDefault("waiting", 0));
        seriesData.add(stateCount.getOrDefault("success", 0));
        seriesData.add(stateCount.getOrDefault("fail", 0));

        Map<String, Object> result = new HashMap<>();
        result.put("xAxis", xAxis);
        result.put("seriesData", seriesData);
        return result;
    }

    @GetMapping("/bar")
    public Map<String, Object> bar(@RequestParam Map<String, Object> params) {
        return line(params);
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

    private Map<String, Integer> queryStateCountWithPermission(Map<String, Object> params) {
        Map<String, Object> queryParams = params == null ? new HashMap<>() : new HashMap<>(params);
        SmsUser smsUser = (SmsUser) SecurityUtils.getSubject().getPrincipal();
        if (smsUser == null) {
            return emptyStateCount();
        }

        String clientIDStr = toStr(queryParams.get("clientID"));
        Long clientID = null;
        if (StringUtils.hasText(clientIDStr)) {
            clientID = toLong(clientIDStr);
        }

        Set<String> roleNames = roleService.getRoleName(smsUser.getId());
        if (roleNames != null && !roleNames.contains(WebMasterConstants.ROOT)) {
            List<ClientBusiness> clients = clientBusinessService.findByUserId(smsUser.getId());
            if (clientID == null) {
                List<Long> list = new ArrayList<>();
                for (ClientBusiness client : clients) {
                    list.add(client.getId());
                }
                queryParams.put("clientID", list);
            } else {
                boolean allow = false;
                for (ClientBusiness client : clients) {
                    if (clientID.equals(client.getId())) {
                        allow = true;
                        break;
                    }
                }
                if (!allow) {
                    return emptyStateCount();
                }
            }
        }

        Map<String, Integer> stateCount = searchClient.countSmsState(queryParams);
        if (stateCount == null || stateCount.isEmpty()) {
            return emptyStateCount();
        }
        Map<String, Integer> result = new HashMap<>();
        result.put("waiting", stateCount.getOrDefault("waiting", 0));
        result.put("success", stateCount.getOrDefault("success", 0));
        result.put("fail", stateCount.getOrDefault("fail", 0));
        return result;
    }

    private Map<String, Integer> emptyStateCount() {
        Map<String, Integer> result = new HashMap<>();
        result.put("waiting", 0);
        result.put("success", 0);
        result.put("fail", 0);
        return result;
    }

    private String toStr(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }
}
