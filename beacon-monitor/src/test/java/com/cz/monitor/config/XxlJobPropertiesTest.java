package com.cz.monitor.config;

import org.junit.Assert;
import org.junit.Test;

public class XxlJobPropertiesTest {

    @Test
    public void shouldTrimOptionalFieldsAndKeepValidConfiguration() {
        XxlJobProperties properties = validProperties();
        properties.setAccessToken(" token-1 ");
        properties.getExecutor().setAddress(" http://127.0.0.1:9999 ");
        properties.getExecutor().setIp(" 192.168.1.10 ");

        properties.validate();

        Assert.assertEquals("http://127.0.0.1:9999", properties.getExecutor().getAddress());
        Assert.assertEquals("192.168.1.10", properties.getExecutor().getIp());
        Assert.assertEquals("token-1", properties.getAccessToken());
    }

    @Test
    public void shouldRejectBlankAdminAddresses() {
        XxlJobProperties properties = validProperties();
        properties.getAdmin().setAddresses("   ");

        try {
            properties.validate();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("xxl.job.admin.addresses"));
        }
    }

    @Test
    public void shouldRejectInvalidPort() {
        XxlJobProperties properties = validProperties();
        properties.getExecutor().setPort(0);

        try {
            properties.validate();
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage().contains("xxl.job.executor.port"));
        }
    }

    private static XxlJobProperties validProperties() {
        XxlJobProperties properties = new XxlJobProperties();
        properties.getAdmin().setAddresses("http://127.0.0.1:8080/xxl-job-admin");
        properties.getExecutor().setAppname("beacon-monitor");
        properties.getExecutor().setPort(9999);
        properties.getExecutor().setLogPath("logs/xxl-job");
        properties.getExecutor().setLogRetentionDays(30);
        return properties;
    }
}
