package com.cz.cache.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cache.security")
public class CacheSecurityProperties {

    private boolean enabled = true;
    private boolean testApiEnabled = false;
    private long maxTimeSkewSeconds = 300;
    private Map<String, String> callerSecrets = new HashMap<>();
    private Map<String, List<String>> callerPermissions = new HashMap<>();
    private List<String> keyPatternAllowList = new ArrayList<>();

    public boolean hasPermission(String caller, CachePermission permission) {
        List<String> permissions = callerPermissions.get(caller);
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        String expected = permission.name();
        for (String value : permissions) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (CachePermission.ADMIN.name().equals(normalized) || expected.equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTestApiEnabled() {
        return testApiEnabled;
    }

    public void setTestApiEnabled(boolean testApiEnabled) {
        this.testApiEnabled = testApiEnabled;
    }

    public long getMaxTimeSkewSeconds() {
        return maxTimeSkewSeconds;
    }

    public void setMaxTimeSkewSeconds(long maxTimeSkewSeconds) {
        this.maxTimeSkewSeconds = maxTimeSkewSeconds;
    }

    public Map<String, String> getCallerSecrets() {
        return callerSecrets;
    }

    public void setCallerSecrets(Map<String, String> callerSecrets) {
        this.callerSecrets = callerSecrets;
    }

    public Map<String, List<String>> getCallerPermissions() {
        return callerPermissions;
    }

    public void setCallerPermissions(Map<String, List<String>> callerPermissions) {
        this.callerPermissions = callerPermissions;
    }

    public List<String> getKeyPatternAllowList() {
        return keyPatternAllowList;
    }

    public void setKeyPatternAllowList(List<String> keyPatternAllowList) {
        this.keyPatternAllowList = keyPatternAllowList;
    }
}
