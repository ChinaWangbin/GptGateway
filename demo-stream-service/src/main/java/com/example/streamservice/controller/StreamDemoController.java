package com.example.streamservice.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/stream")
public class StreamDemoController {

    private final AtomicLong sequence = new AtomicLong(0);

    /**
     * SSE 流式接口。
     * count 参数用于控制事件输出条数（默认 20 条）。
     */
    @GetMapping(value = "/ticks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> ticks(@RequestParam(name = "count", defaultValue = "20") long count) {
        return Flux.interval(Duration.ofSeconds(1))
                .take(count)
                .map(i -> {
                    long id = sequence.incrementAndGet();
                    String payload = "tick-" + id + " @ " + LocalDateTime.now();
                    return ServerSentEvent.<String>builder()
                            .id(String.valueOf(id))
                            .event("tick")
                            .data(payload)
                            .build();
                });
    }

    /**
     * 模拟真实模型流式返回（按 token/片段逐步输出）。
     */
    @GetMapping(value = "/model/mock", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> mockModelStream(
            @RequestParam(name = "prompt", defaultValue = "请介绍一下模型网关的作用。") String prompt) {

        long streamId = sequence.incrementAndGet();
        List<String> chunks = List.of(
                "模型网关的核心作用是统一入口，",
                "把不同模型服务屏蔽在后面，",
                "对上层应用提供稳定的一致接口。",
                "它还可以集中处理鉴权、",
                "限流、审计和路由策略，",
                "降低业务侧的接入复杂度。");

        Flux<ServerSentEvent<String>> head = Flux.just(ServerSentEvent.<String>builder()
                .id(streamId + "-0")
                .event("message")
                .data("{\"id\":\"chatcmpl-" + streamId + "\",\"model\":\"mock-gpt-4o-mini\",\"prompt\":\""
                        + escapeJson(prompt)
                        + "\",\"delta\":\"\"}")
                .build());

        Flux<ServerSentEvent<String>> body = Flux.fromIterable(chunks)
                .index()
                .delayElements(Duration.ofMillis(450))
                .map(tuple -> {
                    long idx = tuple.getT1() + 1;
                    String delta = tuple.getT2();
                    String payload = "{\"id\":\"chatcmpl-" + streamId
                            + "\",\"model\":\"mock-gpt-4o-mini\",\"index\":" + idx
                            + ",\"delta\":\"" + escapeJson(delta) + "\"}";
                    return ServerSentEvent.<String>builder()
                            .id(streamId + "-" + idx)
                            .event("message")
                            .data(payload)
                            .build();
                });

        Flux<ServerSentEvent<String>> done = Flux.just(ServerSentEvent.<String>builder()
                .id(streamId + "-done")
                .event("done")
                .data("{\"id\":\"chatcmpl-" + streamId + "\",\"finish_reason\":\"stop\"}")
                .build());

        return Flux.concat(head, body, done);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
