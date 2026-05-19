package com.cz.webmaster.controller;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.cz.common.util.Result;
import com.cz.common.vo.PageResultVO;
import com.cz.common.vo.ResultVO;
import com.cz.webmaster.vo.ApiGatewayFilterVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

@RestController
@RequestMapping({"/sys/api-gateway-filter", "/sys/apigatewayfilter"})
public class SysApiGatewayFilterController {

    private static final long CONFIG_TIMEOUT_MS = 3000L;

    @Value("${spring.cloud.nacos.config.server-addr:}")
    private String serverAddr;

    @Value("${spring.cloud.nacos.config.namespace:}")
    private String namespace;

    @Value("${api-gateway-filter.nacos.data-id:beacon-api-dev.yml}")
    private String dataId;

    @Value("${api-gateway-filter.nacos.group:DEFAULT_GROUP}")
    private String group;

    @GetMapping("/list")
    public PageResultVO<?> list() {
        return Result.ok(1L, Collections.singletonList(readSnapshot()));
    }

    @GetMapping("/info/{id}")
    public ResultVO<ApiGatewayFilterVO> info(@PathVariable("id") Long id) {
        return Result.ok(readSnapshot());
    }

    @PostMapping({"/save", "/update", "/del"})
    public ResultVO<?> rejectWrite() {
        return Result.error("API网关过滤器来自Nacos配置，当前仅支持只读查看");
    }

    private ApiGatewayFilterVO readSnapshot() {
        ApiGatewayFilterVO vo = baseVO();
        if (!StringUtils.hasText(serverAddr)) {
            markFailed(vo, "Nacos config server-addr is empty");
            return vo;
        }
        try {
            ConfigService configService = NacosFactory.createConfigService(buildNacosProperties());
            String content = configService.getConfig(dataId, group, CONFIG_TIMEOUT_MS);
            if (!StringUtils.hasText(content)) {
                markFailed(vo, "Nacos配置为空或不存在");
                return vo;
            }
            String filters = readFilters(content);
            if (!StringUtils.hasText(filters)) {
                markFailed(vo, "Nacos配置中未找到 filters");
                return vo;
            }
            vo.setFilters(filters.trim());
            vo.setReadState(1);
            vo.setReadStateText("已读取");
            vo.setMessage("来自Nacos配置");
            return vo;
        } catch (Exception ex) {
            markFailed(vo, "读取Nacos配置失败: " + ex.getMessage());
            return vo;
        }
    }

    private ApiGatewayFilterVO baseVO() {
        ApiGatewayFilterVO vo = new ApiGatewayFilterVO();
        vo.setId(1L);
        vo.setServiceName("beacon-api");
        vo.setDataId(dataId);
        vo.setGroup(group);
        vo.setFilters("");
        vo.setReadState(0);
        vo.setReadStateText("未读取");
        vo.setMessage("");
        return vo;
    }

    private Properties buildNacosProperties() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        if (StringUtils.hasText(namespace)) {
            properties.put(PropertyKeyConst.NAMESPACE, namespace);
        }
        return properties;
    }

    @SuppressWarnings("unchecked")
    private String readFilters(String content) {
        Object loaded = new Yaml().load(content);
        if (!(loaded instanceof Map)) {
            return null;
        }
        Object filters = ((Map<String, Object>) loaded).get("filters");
        if (filters instanceof Iterable) {
            ArrayList<String> values = new ArrayList<>();
            for (Object value : (Iterable<?>) filters) {
                if (value != null && StringUtils.hasText(String.valueOf(value))) {
                    values.add(String.valueOf(value).trim());
                }
            }
            return String.join(",", values);
        }
        return filters == null ? null : String.valueOf(filters);
    }

    private void markFailed(ApiGatewayFilterVO vo, String message) {
        vo.setReadState(0);
        vo.setReadStateText("未读取");
        vo.setMessage(message);
    }
}
