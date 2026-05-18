package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.dynamic-route")
public class DynamicRouteProperties {

    private boolean enabled = true;

    private String dataId = "gateway-routes.yaml";

    private String group = "GATEWAY_GROUP";

    private long timeoutMs = 3000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
