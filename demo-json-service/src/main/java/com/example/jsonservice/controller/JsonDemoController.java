package com.example.jsonservice.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
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
}
