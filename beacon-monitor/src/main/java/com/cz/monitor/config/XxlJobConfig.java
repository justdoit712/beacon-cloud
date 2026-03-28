package com.cz.monitor.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * @author cz
 * @version V1.0.0
 */
@Configuration
@Slf4j
public class XxlJobConfig {

    private final XxlJobProperties properties;

    public XxlJobConfig(XxlJobProperties properties) {
        this.properties = properties;
    }

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(properties.getAdmin().getAddresses());
        xxlJobSpringExecutor.setAppname(properties.getExecutor().getAppname());
        if (StringUtils.hasText(properties.getExecutor().getAddress())) {
            xxlJobSpringExecutor.setAddress(properties.getExecutor().getAddress());
        }
        if (StringUtils.hasText(properties.getExecutor().getIp())) {
            xxlJobSpringExecutor.setIp(properties.getExecutor().getIp());
        }
        xxlJobSpringExecutor.setPort(properties.getExecutor().getPort());
        xxlJobSpringExecutor.setAccessToken(properties.getAccessToken());
        xxlJobSpringExecutor.setLogPath(properties.getExecutor().getLogPath());
        xxlJobSpringExecutor.setLogRetentionDays(properties.getExecutor().getLogRetentionDays());
        return xxlJobSpringExecutor;
    }
}
