# Nacos 配置清单

## 命名空间

- 命名空间名称：`modelGateway`
- 命名空间 ID：`modelGateway`

## 配置 1：网关动态路由（SSE 服务）

- Data ID: `gateway-routes.yaml`
- Group: `GATEWAY_GROUP`
- 类型: `YAML`
- Namespace: `modelGateway`

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: stream-service-sse-route
          uri: http://localhost:18082
          predicates:
            - Path=/stream-api/**
          filters:
            - StripPrefix=1
```

网关会监听该配置。发布新增、修改或删除路由后，`gateway` 会自动刷新路由缓存；如果 YAML 格式错误，网关保留上一版可用路由。

## 服务注册信息（Nacos）

以下服务会注册到 `modelGateway`：

- `gpt-gateway`（端口 8080）
- `demo-json-service`（端口 18081）

以下服务不注册到 Nacos：

- `demo-stream-service`（端口 18082，走 Nacos 动态路由）
