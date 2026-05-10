package com.example.gateway.filter;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.example.gateway.config.GatewayFilterProperties;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Order(AccessLogGlobalFilter.ORDER)
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    public static final int ORDER = BasicGlobalFilter.ORDER + 5;

    private static final Logger log = LoggerFactory.getLogger(AccessLogGlobalFilter.class);

    private static final List<MediaType> LOGGABLE_MEDIA_TYPES = List.of(
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_XML,
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_XML,
            MediaType.APPLICATION_FORM_URLENCODED,
            MediaType.TEXT_EVENT_STREAM);

    private final GatewayFilterProperties properties;

    public AccessLogGlobalFilter(GatewayFilterProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayFilterProperties.Logging logging = properties.getLogging();
        if (!logging.isEnabled()) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        AtomicLong responseBytes = new AtomicLong();
        BodyPreview requestPreview = new BodyPreview(logging.getMaxBodyLogSize());
        BodyPreview responsePreview = new BodyPreview(logging.getMaxBodyLogSize());

        ServerHttpRequest request = exchange.getRequest();
        String traceId = String.valueOf(exchange.getAttributeOrDefault(BasicGlobalFilter.TRACE_ID_ATTRIBUTE, ""));
        log.info("Gateway request start traceId={}, method={}, path={}, query={}, remote={}",
                traceId, request.getMethod(), request.getURI().getRawPath(), request.getURI().getRawQuery(),
                request.getRemoteAddress());

        ServerHttpRequest decoratedRequest = decorateRequest(request, logging, requestPreview);
        ServerHttpResponse decoratedResponse = decorateResponse(exchange.getResponse(), logging, responsePreview,
                responseBytes);

        return chain.filter(exchange.mutate().request(decoratedRequest).response(decoratedResponse).build())
                .doOnError(throwable -> log.warn("Gateway request error traceId={}, message={}",
                        traceId, throwable.getMessage(), throwable))
                .doFinally(signalType -> log.info(
                        "Gateway request end traceId={}, status={}, durationMs={}, responseBytes={}, requestBody={}, responseBody={}",
                        traceId, decoratedResponse.getStatusCode(), System.currentTimeMillis() - startTime,
                        responseBytes.get(), requestPreview.value(), responsePreview.value()));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private ServerHttpRequest decorateRequest(ServerHttpRequest request, GatewayFilterProperties.Logging logging,
            BodyPreview requestPreview) {
        if (!logging.isLogRequestBody() || !isLoggable(request.getHeaders())) {
            return request;
        }
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(dataBuffer -> requestPreview.append(dataBuffer));
            }
        };
    }

    private ServerHttpResponse decorateResponse(ServerHttpResponse response, GatewayFilterProperties.Logging logging,
            BodyPreview responsePreview, AtomicLong responseBytes) {
        return new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                Flux<? extends DataBuffer> loggingBody = Flux.from(body).doOnNext(dataBuffer -> {
                    responseBytes.addAndGet(dataBuffer.readableByteCount());
                    if (logging.isLogResponseBody() && isLoggable(getHeaders())) {
                        responsePreview.append(dataBuffer);
                    }
                });
                return super.writeWith(loggingBody);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                Flux<Publisher<? extends DataBuffer>> loggingBody = Flux.from(body).map(publisher -> Flux.from(publisher)
                        .doOnNext(dataBuffer -> {
                            responseBytes.addAndGet(dataBuffer.readableByteCount());
                            if (logging.isLogResponseBody() && isLoggable(getHeaders())) {
                                responsePreview.append(dataBuffer);
                            }
                        }));
                return super.writeAndFlushWith(loggingBody);
            }
        };
    }

    private boolean isLoggable(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType == null) {
            return true;
        }
        return LOGGABLE_MEDIA_TYPES.stream().anyMatch(loggable -> loggable.isCompatibleWith(contentType));
    }

    private static class BodyPreview {

        private final int limit;

        private final AtomicInteger remaining;

        private final StringBuilder value = new StringBuilder();

        BodyPreview(int limit) {
            this.limit = Math.max(limit, 0);
            this.remaining = new AtomicInteger(this.limit);
        }

        void append(DataBuffer dataBuffer) {
            int allowed = remaining.get();
            if (allowed <= 0) {
                return;
            }

            int length = Math.min(dataBuffer.readableByteCount(), allowed);
            ByteBuffer byteBuffer = dataBuffer.toByteBuffer(dataBuffer.readPosition(), length).asReadOnlyBuffer();
            byte[] bytes = new byte[length];
            byteBuffer.get(bytes);
            value.append(new String(bytes, StandardCharsets.UTF_8));
            remaining.addAndGet(-length);
        }

        String value() {
            if (value.isEmpty()) {
                return "";
            }
            if (value.length() >= limit && remaining.get() == 0) {
                return value + "...";
            }
            return value.toString();
        }
    }
}
