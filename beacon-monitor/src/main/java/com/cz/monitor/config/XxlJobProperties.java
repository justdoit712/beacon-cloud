package com.cz.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

/**
 * XXL-Job 配置模型。
 *
 * <p>统一承接 {@code xxl.job.*} 配置，并在启动阶段做最小必需项校验，
 * 避免配置键存在但运行时未真正生效或空值启动。</p>
 */
@Component
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    private final Admin admin = new Admin();

    private String accessToken;

    private final Executor executor = new Executor();

    @PostConstruct
    public void validate() {
        admin.setAddresses(requireText(admin.getAddresses(), "xxl.job.admin.addresses"));
        accessToken = trimToNull(accessToken);

        executor.setAppname(requireText(executor.getAppname(), "xxl.job.executor.appname"));
        executor.setAddress(trimToNull(executor.getAddress()));
        executor.setIp(trimToNull(executor.getIp()));
        executor.setLogPath(requireText(executor.getLogPath(), "xxl.job.executor.logpath"));

        if (executor.getPort() <= 0 || executor.getPort() > 65535) {
            throw new IllegalArgumentException("xxl.job.executor.port must be between 1 and 65535");
        }
        if (executor.getLogRetentionDays() < 1) {
            throw new IllegalArgumentException("xxl.job.executor.logretentiondays must be greater than 0");
        }
    }

    private static String requireText(String value, String name) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public Admin getAdmin() {
        return admin;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Executor getExecutor() {
        return executor;
    }

    public static class Admin {

        private String addresses;

        public String getAddresses() {
            return addresses;
        }

        public void setAddresses(String addresses) {
            this.addresses = addresses;
        }
    }

    public static class Executor {

        private String appname;

        private String address;

        private String ip;

        private int port;

        private String logPath;

        private int logRetentionDays;

        public String getAppname() {
            return appname;
        }

        public void setAppname(String appname) {
            this.appname = appname;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }

        public int getLogRetentionDays() {
            return logRetentionDays;
        }

        public void setLogRetentionDays(int logRetentionDays) {
            this.logRetentionDays = logRetentionDays;
        }
    }
}
