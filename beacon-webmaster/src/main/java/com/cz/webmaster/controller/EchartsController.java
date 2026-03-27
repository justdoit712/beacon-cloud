package com.cz.webmaster.controller;

import com.cz.common.util.Result;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.service.EchartsQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图表相关接口
 */
@RestController
@RequestMapping("/sys/echarts")
public class EchartsController {

    private final EchartsQueryService echartsQueryService;

    public EchartsController(EchartsQueryService echartsQueryService) {
        this.echartsQueryService = echartsQueryService;
    }

    @GetMapping("/pie")
    public ResultVO<?> pie(@RequestParam Map<String, Object> params) {
        Map<String, Integer> stateCount = echartsQueryService.queryStateCountWithPermission(params);
        int waiting = stateCount.getOrDefault("waiting", 0);
        int success = stateCount.getOrDefault("success", 0);
        int fail = stateCount.getOrDefault("fail", 0);

        List<String> legendData = new ArrayList<>();
        legendData.add("等待");
        legendData.add("成功");
        legendData.add("失败");

        List<Map<String, Object>> seriesData = new ArrayList<>();
        Map<String, Object> waitingItem = new LinkedHashMap<>();
        waitingItem.put("name", "等待");
        waitingItem.put("value", waiting);
        seriesData.add(waitingItem);

        Map<String, Object> successItem = new LinkedHashMap<>();
        successItem.put("name", "成功");
        successItem.put("value", success);
        seriesData.add(successItem);

        Map<String, Object> failItem = new LinkedHashMap<>();
        failItem.put("name", "失败");
        failItem.put("value", fail);
        seriesData.add(failItem);

        Map<String, Object> result = new HashMap<>();
        result.put("legendData", legendData);
        result.put("seriesData", seriesData);
        return Result.ok(result);
    }

    @GetMapping("/line")
    public ResultVO<?> line(@RequestParam Map<String, Object> params) {
        Map<String, Integer> stateCount = echartsQueryService.queryStateCountWithPermission(params);

        List<String> xAxis = new ArrayList<>();
        xAxis.add("等待");
        xAxis.add("成功");
        xAxis.add("失败");

        List<Integer> seriesData = new ArrayList<>();
        seriesData.add(stateCount.getOrDefault("waiting", 0));
        seriesData.add(stateCount.getOrDefault("success", 0));
        seriesData.add(stateCount.getOrDefault("fail", 0));

        Map<String, Object> result = new HashMap<>();
        result.put("xAxis", xAxis);
        result.put("seriesData", seriesData);
        return Result.ok(result);
    }

    @GetMapping("/bar")
    public ResultVO<?> bar(@RequestParam Map<String, Object> params) {
        return line(params);
    }
}
