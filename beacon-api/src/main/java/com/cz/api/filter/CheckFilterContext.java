package com.cz.api.filter;


import com.cz.common.model.StandardSubmit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author cz
 * @description 策略模式的上下文对象
 */
@Component
@RefreshScope
public class CheckFilterContext {

    // Spring的IOC会将对象全部都放到Map集合中
    // 基于4.x中Spring提供的反省注解，基于Map只拿到需要的类型对象即可
    @Autowired
    private Map<String, CheckFilter> checkFiltersMap;

    // 基于Nacos获取到执行的顺序和需要执行的校验对象
    @Value("${filters:apikey,ip,sign,template}")
    private String filters;

    @PostConstruct
    public void validateFilters() {
        for (String filterName : resolveFilterNames()) {
            if (!checkFiltersMap.containsKey(filterName)) {
                throw new IllegalStateException("unknown check filter: " + filterName);
            }
        }
    }

    /**
     * 当前check方法用于管理校验链的顺序
     */
    public void check(StandardSubmit submit) {
        for (String filterName : resolveFilterNames()) {
            CheckFilter checkFilter = checkFiltersMap.get(filterName);
            if (checkFilter == null) {
                throw new IllegalStateException("unknown check filter: " + filterName);
            }
            checkFilter.check(submit);
        }
    }

    private List<String> resolveFilterNames() {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(filters)) {
            return result;
        }
        String[] filterArray = filters.split(",");
        for (String filter : filterArray) {
            if (!StringUtils.hasText(filter)) {
                continue;
            }
            String filterName = filter.trim();
            if (!filterName.isEmpty()) {
                result.add(filterName);
            }
        }
        return result;
    }
}
