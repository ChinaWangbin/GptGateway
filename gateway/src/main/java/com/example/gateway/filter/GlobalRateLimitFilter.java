package com.example.gateway.filter;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.gateway.config.GatewayFilterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
@Order(GlobalRateLimitFilter.ORDER)
public class GlobalRateLimitFilter implements GlobalFilter, Ordered {

    public static final int ORDER = BasicGlobalFilter.ORDER + 10;

    private static final Logger log = LoggerFactory.getLogger(GlobalRateLimitFilter.class);

    private final GatewayFilterProperties properties;

    private final Clock clock;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private volatile long lastCleanupTime;

    /**
     * 生产环境使用的构造方法，使用系统 UTC 时钟计算令牌补充时间。
     */
    @Autowired
    public GlobalRateLimitFilter(GatewayFilterProperties properties) {
        this(properties, Clock.systemUTC());
    }

    /**
     * 可注入时钟的构造方法，主要方便后续写单元测试时控制时间流逝。
     */
    GlobalRateLimitFilter(GatewayFilterProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.lastCleanupTime = clock.millis();
    }

    /**
     * 网关全局限流入口。每个请求先按客户端维度找到令牌桶，令牌充足则继续执行过滤器链，
     * 令牌不足则直接返回 429，避免请求继续转发到下游服务。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayFilterProperties.RateLimit rateLimit = properties.getRateLimit();
        if (!rateLimit.isEnabled()) {
            return chain.filter(exchange);
        }

        String key = resolveKey(exchange.getRequest());
        TokenBucket bucket = buckets.computeIfAbsent(key,
                ignored -> new TokenBucket(rateLimit.getBurstCapacity(), clock.millis()));
        if (bucket.tryConsume(rateLimit.getReplenishRate(), rateLimit.getBurstCapacity(), clock.millis())) {
            cleanupExpiredBuckets(rateLimit.getCleanupInterval(), clock.millis());
            return chain.filter(exchange);
        }

        String traceId = String.valueOf(exchange.getAttributeOrDefault(BasicGlobalFilter.TRACE_ID_ATTRIBUTE, ""));
        log.warn("Gateway rate limited request traceId={}, key={}, method={}, path={}",
                traceId, key, exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath());
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        return exchange.getResponse().setComplete();
    }

    /**
     * 返回当前过滤器在 Gateway 全局过滤器链中的执行顺序。
     */
    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 解析当前请求的限流 key。优先使用代理透传的 X-Forwarded-For 首个 IP，
     * 没有该请求头时再使用连接上的 remote address。
     */
    private String resolveKey(ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }

    /**
     * 清理长时间没有访问的令牌桶，防止客户端 IP 不断增加导致 buckets 占用内存持续增长。
     * 这里复用 cleanupInterval 作为扫描间隔和过期阈值，让配置保持简单。
     */
    private void cleanupExpiredBuckets(Duration cleanupInterval, long now) {
        if (cleanupInterval == null || cleanupInterval.isZero() || cleanupInterval.isNegative()) {
            return;
        }
        if (now - lastCleanupTime < cleanupInterval.toMillis()) {
            return;
        }
        lastCleanupTime = now;
        Iterator<Map.Entry<String, TokenBucket>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TokenBucket> entry = iterator.next();
            if (now - entry.getValue().lastRefillTime > cleanupInterval.toMillis()) {
                iterator.remove();
            }
        }
    }

    private static class TokenBucket {

        // 当前桶里剩余的令牌数。使用 double 是为了保留按毫秒补充令牌时产生的小数部分。
        private double tokens;

        // 上一次计算补充令牌的时间，用于根据时间差推导这次应该补多少令牌。
        private long lastRefillTime;

        /**
         * 新客户端首次访问时创建令牌桶。初始令牌数等于突发容量，
         * 这样服务启动或新客户端接入后可以立即承载 burstCapacity 范围内的突发请求。
         */
        TokenBucket(int burstCapacity, long now) {
            this.tokens = burstCapacity;
            this.lastRefillTime = now;
        }

        /**
         * 尝试消费一个令牌。该方法使用 synchronized 保证同一个客户端并发请求时，
         * 补充令牌和扣减令牌这两个动作按顺序执行，不会出现并发覆盖。
         */
        synchronized boolean tryConsume(int replenishRate, int burstCapacity, long now) {
            // 每次请求进来时先按经过的时间补充令牌，避免依赖额外定时任务。
            refill(replenishRate, burstCapacity, now);
            // 令牌不足 1 个时拒绝请求；上层会返回 429 Too Many Requests。
            if (tokens < 1) {
                return false;
            }
            // 允许通过的请求消耗 1 个令牌。
            tokens -= 1;
            return true;
        }

        /**
         * 按时间差补充令牌。令牌桶不是通过定时任务主动补充，而是在请求到达时根据
         * “当前时间 - 上次补充时间”计算应补令牌数，并把最终令牌数限制在 burstCapacity 内。
         */
        private void refill(int replenishRate, int burstCapacity, long now) {
            // 计算距离上次补充经过了多少毫秒；Math.max 防止系统时间回拨导致负数。
            long elapsedMillis = Math.max(0, now - lastRefillTime);
            if (elapsedMillis == 0) {
                return;
            }
            // replenishRate 表示每秒补充多少令牌，所以毫秒差需要除以 1000 换算成秒。
            double refillTokens = elapsedMillis * Math.max(replenishRate, 0) / 1000D;
            // 令牌最多只能补到 burstCapacity，burstCapacity 就是允许的瞬时突发容量。
            tokens = Math.min(Math.max(burstCapacity, 1), tokens + refillTokens);
            lastRefillTime = now;
        }
    }
}
