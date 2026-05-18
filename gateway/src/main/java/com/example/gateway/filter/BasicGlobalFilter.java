package com.example.gateway.filter;

import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@Component
@Order(BasicGlobalFilter.ORDER)
public class BasicGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    /**
     * 启用 Reactor 自动 MDC 上下文传播。
     * 在异步边界（如下游 IO 等待/返回）处自动恢复当前请求的 MDC context，
     * 避免事件循环线程被复用后 traceId 被其他请求污染。
     */
    @PostConstruct
    public void enableMdcPropagation() {
        Hooks.enableAutomaticContextPropagation();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = resolveTraceId(exchange.getRequest());
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(TRACE_ID_HEADER, traceId)
                .build();
        exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);

        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(context -> context.put(TRACE_ID_ATTRIBUTE, traceId))
                .doFirst(() -> MDC.put(TRACE_ID_ATTRIBUTE, traceId))
                .doFinally(signalType -> MDC.remove(TRACE_ID_ATTRIBUTE));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private String resolveTraceId(ServerHttpRequest request) {
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return traceId;
    }
}
