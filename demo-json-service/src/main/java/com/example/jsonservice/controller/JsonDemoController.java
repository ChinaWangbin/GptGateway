package com.example.jsonservice.controller;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class JsonDemoController {

    @Value("${spring.application.name}")
    private String appName;

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", appName);
        body.put("type", "json");
        body.put("message", "hello from json service");
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }

    @PostMapping("/v1/chat/completions")
    public Map<String, Object> mockOpenAiChatCompletion(@RequestBody(required = false) Map<String, Object> request) {
        String userPrompt = "你好，请介绍一下你自己。";
        String requestModel = "gpt-4o-mini";
        if (request != null) {
            Object model = request.get("model");
            if (model != null && !model.toString().isBlank()) {
                requestModel = model.toString();
            }
            Object messagesRaw = request.get("messages");
            if (messagesRaw instanceof List<?> messages && !messages.isEmpty()) {
                for (int i = messages.size() - 1; i >= 0; i--) {
                    Object item = messages.get(i);
                    if (item instanceof Map<?, ?> map) {
                        Object role = map.get("role");
                        Object content = map.get("content");
                        if ("user".equals(String.valueOf(role)) && content != null && !content.toString().isBlank()) {
                            userPrompt = content.toString();
                            break;
                        }
                    }
                }
            }
        }

        String completionText = "这是一个模拟的 OpenAI Chat Completions 返回。你刚刚的问题是："
                + userPrompt
                + "。该响应用于联调网关与上游模型协议。";

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", completionText);

        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        List<Map<String, Object>> choices = new ArrayList<>();
        choices.add(choice);

        int promptTokens = Math.max(10, userPrompt.length() / 2);
        int completionTokens = Math.max(20, completionText.length() / 2);

        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
        body.put("object", "chat.completion");
        body.put("created", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        body.put("model", requestModel);
        body.put("choices", choices);
        body.put("usage", usage);
        body.put("system_fingerprint", "fp_mock_gateway_demo");
        return body;
    }
}
