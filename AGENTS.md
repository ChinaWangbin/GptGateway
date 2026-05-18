# AGENTS 指南（GptGateway）

## 指令约束

```text
Always respond in Chinese-simplified
```

## 项目概览

- 工作目录：`G:\MyWorkspace\GptGateway`
- 项目类型：Maven 多模块
- 技术栈：
  - Spring Boot `3.0.2`
  - Spring Cloud `2022.0.0`
  - Spring Cloud Alibaba `2022.0.0.0`
  - Java `17`
  - Nacos `2.x`

## 模块结构

- `gateway`：网关主服务（Spring Cloud Gateway），端口 8080
- `demo-json-service`：JSON 测试服务，注册到 Nacos，端口 18081
- `demo-stream-service`：SSE 流式测试服务，不注册 Nacos，走静态路由，端口 18082

## Gateway 全局过滤器（执行顺序）

### 1. BasicGlobalFilter（优先级最高）
- 为每个请求生成或透传 `X-Trace-Id` 头部和属性
- 将 traceId 写入 SLF4J MDC，方便日志链路追踪

### 2. AccessLogGlobalFilter
- 记录请求开始（method、path、query、remote）
- 记录请求结束（status、durationMs、responseBytes）
- 支持对 JSON / XML / SSE 等 Content-Type 的请求体和响应体做预览式日志记录
- 默认最大记录 `2048` 字节，超出末尾追加 `...`
- 响应体采用旁路预览方式，不聚合完整响应，避免破坏 SSE 流式返回

### 3. GlobalRateLimitFilter
- 手写令牌桶实现，按 IP 粒度限流
- 限流 key 优先取 `X-Forwarded-For` 首个 IP，没有代理头时取 `remoteAddress`
- 配置项：`replenishRate`（每秒补充令牌数，默认 50）、`burstCapacity`（瞬时突发容量，默认 100）
- 令牌不足时直接返回 `429 Too Many Requests`
- 定期清理长时间无访问的 IP 桶，防止内存泄漏

所有过滤器通过 `GatewayFilterProperties` 集中配置（前缀 `gateway.filters`），支持开关控制。

## 动态路由刷新

`NacosDynamicRouteRefresher` 组件：
- 启动时从 Nacos 拉取 `gateway-routes.yaml` 初始化路由
- 注册 Nacos 配置监听器，配置变更时自动刷新路由并发布 `RefreshRoutesEvent`
- 解析失败时保留已有路由，不阻断服务
- 通过 `DynamicRouteProperties` 配置（dataId / group / timeoutMs）

## 路由模式

### 模式一：Nacos 配置中心自定义路由（SSE 服务）
- 从 Nacos `modelGateway` 命名空间获取 `gateway-routes.yaml`
- 静态路由到 `http://localhost:18082`，路径前缀 `/stream-api/**`
- 通过 `spring.config.import` 和 `NacosDynamicRouteRefresher` 双重加载

### 模式二：Nacos 服务发现自动路由（JSON 服务）
- `discovery.locator.enabled=true` + `lower-case-service-id=true`
- 自动生成 `/{service-id}/**` -> `lb://{service-id}` 的路由
- 访问路径：`/demo-json-service/**`

## Nacos 约定

- Nacos 地址：`http://localhost:8848/nacos`
- 账号密码：`nacos / nacos`
- 命名空间名称：`modelGateway`
- 命名空间 ID：`modelGateway`

## 关键配置文件

- 根依赖管理：`pom.xml`
- 网关配置：`gateway/src/main/resources/application.yml`
- 过滤器配置属性：`gateway/src/main/java/com/example/gateway/config/GatewayFilterProperties.java`
- 动态路由配置：`gateway/src/main/java/com/example/gateway/config/DynamicRouteProperties.java`
- 动态路由刷新器：`gateway/src/main/java/com/example/gateway/route/NacosDynamicRouteRefresher.java`
- 基础过滤器：`gateway/src/main/java/com/example/gateway/filter/BasicGlobalFilter.java`
- 访问日志过滤器：`gateway/src/main/java/com/example/gateway/filter/AccessLogGlobalFilter.java`
- 限流过滤器：`gateway/src/main/java/com/example/gateway/filter/GlobalRateLimitFilter.java`
- JSON 服务控制器：`demo-json-service/src/main/java/com/example/jsonservice/controller/JsonDemoController.java`
- SSE 服务控制器：`demo-stream-service/src/main/java/com/example/streamservice/controller/StreamDemoController.java`
- Nacos 路由模板：`nacos-config/gateway-routes.yaml`

## 接口文档

### demo-json-service（直连端口 18081）

**GET /api/hello** — 基础 JSON 问候接口

请求：
```bash
curl "http://localhost:18081/api/hello"
```

响应（200 OK）：
```json
{
  "service": "demo-json-service",
  "type": "json",
  "message": "hello from json service",
  "timestamp": "2026-05-18T09:52:48.1132325"
}
```

---

**POST /api/v1/chat/completions** — 模拟 OpenAI Chat Completions 非流式接口

请求：
```bash
curl -X POST "http://localhost:18081/api/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gpt-4o",
    "messages": [
      {"role": "user", "content": "你是谁"}
    ]
  }'
```

响应（200 OK）：
```json
{
  "id": "chatcmpl-778b178958cc4f8ab657e7be5f647e43",
  "object": "chat.completion",
  "created": 1779097968,
  "model": "gpt-4o",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "这是一个模拟的 OpenAI Chat Completions 返回。你刚刚的问题是：你是谁。该响应用于联调网关与上游模型协议。"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 32,
    "total_tokens": 42
  },
  "system_fingerprint": "fp_mock_gateway_demo"
}
```

---

### demo-stream-service（直连端口 18082）

**GET /stream/ticks?count=N** — SSE 定时推送接口

请求：
```bash
curl -N "http://localhost:18082/stream/ticks?count=3"
```

响应（`text/event-stream`，每秒输出一条）：
```
id:4
event:tick
data:tick-4 @ 2026-05-18T09:52:49.154950300

id:5
event:tick
data:tick-5 @ 2026-05-18T09:52:50.147240400

id:6
event:tick
data:tick-6 @ 2026-05-18T09:52:51.158424100
```

参数说明：
- `count`（可选，默认 20）：输出的 SSE 事件条数

---

**GET /stream/model/mock?prompt=...** — 模拟模型流式输出接口

请求：
```bash
curl -N "http://localhost:18082/stream/model/mock?prompt=介绍一下模型网关"
```

响应（`text/event-stream`，每段间隔 450ms）：
```
id:3-0
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","prompt":"介绍一下模型网关","delta":""}

id:3-1
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","index":1,"delta":"模型网关的核心作用是统一入口，"}

id:3-2
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","index":2,"delta":"把不同模型服务屏蔽在后面，"}

id:3-3
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","index":3,"delta":"对上层应用提供稳定的一致接口。"}

id:3-4
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","index":4,"delta":"它还可以集中处理鉴权、"}

id:3-5
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","index":5,"delta":"限流、审计和路由策略，"}

id:3-6
event:message
data:{"id":"chatcmpl-3","model":"mock-gpt-4o-mini","index":6,"delta":"降低业务侧的接入复杂度。"}

id:3-done
event:done
data:{"id":"chatcmpl-3","finish_reason":"stop"}
```

参数说明：
- `prompt`（可选，默认"请介绍一下模型网关关的作用。"）：用于回显到首条 SSE 事件中
- 输出流程：head（message） -> 6 个 chunk（message，每段 450ms） -> done
- SSE 事件类型：`message`（具体数据）、`done`（结束信号）

---

### gateway（端口 8080，需先启动所有服务）

通过网关访问的路由路径：

```bash
# 自动路由（JSON 服务）
curl "http://localhost:8080/demo-json-service/api/hello"

# 自定义路由（SSE 服务）
curl -N "http://localhost:8080/stream-api/stream/ticks?count=5"

# 自定义路由（SSE 模拟模型流）
curl -N "http://localhost:8080/stream-api/stream/model/mock?prompt=你好"
```

## 启动步骤

1. 启动 Nacos。
2. 在 Nacos `modelGateway` 命名空间导入 `gateway-routes.yaml`（Data ID：`gateway-routes.yaml`，Group：`GATEWAY_GROUP`）。
3. 在项目根目录执行：
   - `mvn clean package -DskipTests`
4. 分别启动服务：
   - `mvn -pl demo-json-service spring-boot:run`
   - `mvn -pl demo-stream-service spring-boot:run`
   - `mvn -pl gateway spring-boot:run`


## 构建说明

由于项目使用 Spring Boot 3.0.2，需要 Maven 3.5+，系统自带 Maven 3.3.9 无法解析 `spring-boot-maven-plugin`。

- Maven 3.9.6 已安装到 `G:\MyWorkspace\GptGateway\apache-maven-3.9.6`
- 构建命令示例：
  - `mvn -pl gateway -DskipTests clean compile jar:jar spring-boot:repackage`
  - `mvn -pl demo-json-service -DskipTests clean compile jar:jar spring-boot:repackage`
  - `mvn -pl demo-stream-service -DskipTests clean compile jar:jar spring-boot:repackage`
- 启动 Gateway 需用 Java 21 全路径（系统 PATH 默认指向 Java 8）：
  `"C:\Program Files\java21\bin\java.exe" -jar gateway\target\gateway-1.0.0.jar`

## 已知问题

### Gateway 启动卡死（已修复）

**根因**：`NacosDynamicRouteRefresher` 实现 `InitializingBean.afterPropertiesSet()`，在 Bean 初始化阶段调用 `refreshRoutes()`（含 `Binder` 解析 + `GatewayProperties.setRoutes()` + 发布 `RefreshRoutesEvent`），此时 `RouteDefinitionRouteLocator` 尚未就绪，导致死锁。

**修复**：去掉 `InitializingBean`，改用 `@PostConstruct` 只注册 Nacos 监听器，初始路由加载移到 `@EventListener(ApplicationReadyEvent.class)` 中，等应用完全就绪后再执行。

## 维护说明

- 若调整命名空间，需同步修改：
  - `gateway` 的 `nacos.config.namespace` 与 `nacos.discovery.namespace`
  - `demo-json-service` 的 `nacos.discovery.namespace`
  - Nacos 配置发布命名空间
- `demo-stream-service` 当前为"非注册式服务"，如改为注册式，需同步调整：
  - `demo-stream-service` 依赖与配置
  - `gateway-routes.yaml` 的 `uri`（静态地址改 `lb://service-name`）
- 若调整网关日志或限流策略，需同步修改：
  - `gateway/src/main/resources/application.yml` 的 `gateway.filters.*` 配置
  - 对应的全局过滤器实现与本说明文档