package com.example.streamservice.controller;

import java.time.Duration;
import java.time.LocalDateTime;
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
}
