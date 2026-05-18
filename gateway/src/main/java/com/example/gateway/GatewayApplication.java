package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.gateway.config.DynamicRouteProperties;
import com.example.gateway.config.GatewayFilterProperties;

@SpringBootApplication
@EnableConfigurationProperties({ GatewayFilterProperties.class, DynamicRouteProperties.class })
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
