package com.example.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.filters")
public class GatewayFilterProperties {

    private final Logging logging = new Logging();

    private final RateLimit rateLimit = new RateLimit();

    public Logging getLogging() {
        return logging;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Logging {

        private boolean enabled = true;

        private boolean logRequestBody = true;

        private boolean logResponseBody = true;

        private int maxBodyLogSize = 2048;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogRequestBody() {
            return logRequestBody;
        }

        public void setLogRequestBody(boolean logRequestBody) {
            this.logRequestBody = logRequestBody;
        }

        public boolean isLogResponseBody() {
            return logResponseBody;
        }

        public void setLogResponseBody(boolean logResponseBody) {
            this.logResponseBody = logResponseBody;
        }

        public int getMaxBodyLogSize() {
            return maxBodyLogSize;
        }

        public void setMaxBodyLogSize(int maxBodyLogSize) {
            this.maxBodyLogSize = maxBodyLogSize;
        }
    }

    public static class RateLimit {

        private boolean enabled = true;

        private int replenishRate = 50;

        private int burstCapacity = 100;

        private Duration cleanupInterval = Duration.ofMinutes(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }

        public Duration getCleanupInterval() {
            return cleanupInterval;
        }

        public void setCleanupInterval(Duration cleanupInterval) {
            this.cleanupInterval = cleanupInterval;
        }
    }
}
