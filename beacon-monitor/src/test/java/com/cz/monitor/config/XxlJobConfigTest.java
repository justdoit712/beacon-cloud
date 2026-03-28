package com.cz.monitor.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class XxlJobConfigTest {

    @Test
    public void shouldApplyConfiguredAddressToExecutor() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.getAdmin().setAddresses("http://127.0.0.1:8080/xxl-job-admin");
        properties.getExecutor().setAppname("beacon-monitor");
        properties.getExecutor().setAddress("http://127.0.0.1:9999/xxl-job-executor");
        properties.getExecutor().setIp("127.0.0.1");
        properties.getExecutor().setPort(9999);
        properties.getExecutor().setLogPath("logs/xxl-job");
        properties.getExecutor().setLogRetentionDays(30);
        properties.validate();

        XxlJobSpringExecutor executor = new XxlJobConfig(properties).xxlJobExecutor();

        Assert.assertEquals("http://127.0.0.1:9999/xxl-job-executor",
                ReflectionTestUtils.getField(executor, "address"));
        Assert.assertEquals("beacon-monitor", ReflectionTestUtils.getField(executor, "appname"));
        Assert.assertEquals(9999, ReflectionTestUtils.getField(executor, "port"));
    }
}
