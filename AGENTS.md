# AGENTS 指南（GptGateway）

## 指令约束

```text
Always respond in Chinese-simplified
```

## 项目概览

- 工作目录：`G:\MyWorkspace\GptGateway`
- 项目类型：Maven 多模块（parent: `gpt-gateway-platform`）
- 核心技术：Spring Boot 3.0.2 / Spring Cloud 2022.0.0 / Spring Cloud Alibaba 2022.0.0.0 / Java 17 / Nacos 2.x
- 本质：AI 模型网关原型，统一收敛下游模型服务（JSON REST + SSE 流式），提供路由、限流、日志审计等网关层能力

## 模块职责

| 模块 | 角色 | 发现方式 | 端口 |
|---|---|---|---|
| `gateway` | API 网关入口，路由转发+过滤器链 | — | 8080 |
| `demo-json-service` | 模拟 OpenAI Chat Completions JSON 接口 | Nacos 注册 | 18081 |
| `demo-stream-service` | 模拟 SSE 流式模型输出 | 静态路由（不注册 Nacos） | 18082 |
| `nacos-config` | `gateway-routes.yaml` 路由配置模板 | — | — |

## 路由模式

1. **Nacos 服务发现自动路由** — `discovery.locator.enabled=true`，`/{service-id}/**` → `lb://{service-id}`，demo-json-service 走此模式
2. **Nacos 动态自定义路由** — 监听 `gateway-routes.yaml` 配置变更，运行时刷新路由，demo-stream-service 走此模式

## Gateway 全局过滤器（执行顺序）

### 1. BasicGlobalFilter（order=HIGHEST_PRECEDENCE）
- 生成/透传 `X-Trace-Id` 请求头，存入 exchange attribute 和 Reactor Context
- 通过 `doFirst` 写入 SLF4J MDC，配合 `Hooks.enableAutomaticContextPropagation()` 确保异步边界 MDC 正确恢复
- 日志 pattern 通过 `%X{traceId}` 自动输出 traceId

### 2. AccessLogGlobalFilter（order=5）
- 记录请求开始（method/path/query/remote）和结束（status/durationMs/responseBytes）
- 支持 JSON/XML/SSE 等 Content-Type 的请求/响应体预览日志，默认最大 2048 字节
- 响应体旁路预览，不聚合完整响应，不破坏 SSE 流式

### 3. GlobalRateLimitFilter（order=10）
- 手写令牌桶，按 IP 粒度限流（key 优先 `X-Forwarded-For`，其次 `remoteAddress`）
- 配置：replenishRate（50/s）/ burstCapacity（100）/ cleanupInterval（10m）
- 超限返回 429，定期清理过期桶防内存泄漏

所有过滤器通过 `GatewayFilterProperties`（前缀 `gateway.filters`）配置开关。

## Nacos 动态路由刷新

`NacosDynamicRouteRefresher`:
- 启动时拉取 `gateway-routes.yaml` 初始化路由（`ApplicationReadyEvent` 后执行，避免死锁）
- 注册 Nacos 配置监听器，变更时自动解析 YAML → `RouteDefinition` → `RefreshRoutesEvent`
- 解析失败保留旧路由，不阻断服务
- 配置项：dataId / group / timeoutMs

## Nacos 约定

- 地址：`http://82.156.139.106:8848/nacos`，账号：`nacos / nacos`
- 命名空间：`modelGateway`（ID 与名称一致）
- 路由配置：Data ID `gateway-routes.yaml`，Group `GATEWAY_GROUP`

## 已知问题

### Gateway 启动卡死（已修复）
**根因**：`NacosDynamicRouteRefresher` 在 `InitializingBean.afterPropertiesSet()` 阶段调用 `refreshRoutes()`，此时 `RouteDefinitionRouteLocator` 尚未就绪，导致死锁。
**修复**：改用 `@PostConstruct` 只注册 Nacos 监听器，初始路由加载移到 `@EventListener(ApplicationReadyEvent.class)` 中。

## 接口摘要

### demo-json-service（直连 :18081）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/hello` | 基础 JSON 问候 |
| POST | `/api/v1/chat/completions` | 模拟 OpenAI 非流式，解析 `model`/`messages`，返回 mock 响应 |

### demo-stream-service（直连 :18082）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/stream/ticks?count=20` | SSE 定时推送，1s/条 |
| GET | `/stream/model/mock?prompt=...` | 模拟模型流式输出，6 个 chunk 450ms/段 |

### Gateway（:8080）

| 类型 | 路径 | 说明 |
|---|---|---|
| 自动路由 | `http://localhost:8080/demo-json-service/api/hello` | 服务发现 → JSON 服务 |
| 自定义路由 | `http://localhost:8080/stream-api/stream/ticks?count=5` | Nacos 配置 → SSE 服务 |

## 启动步骤

1. 启动 Nacos，在 `modelGateway` 命名空间创建 `gateway-routes.yaml`（Group: `GATEWAY_GROUP`，内容见 `nacos-config/gateway-routes.yaml`）
2. `mvn clean package -DskipTests`
3. 分别启动三个模块：`mvn -pl demo-json-service spring-boot:run`（依赖 Nacos） / `mvn -pl demo-stream-service spring-boot:run` / `mvn -pl gateway spring-boot:run`（依赖 Nacos）

## 维护注意

- 调整命名空间需同步修改 gateway 和 demo-json-service 的 `nacos.config/discovery.namespace`
- demo-stream-service 如改为注册式，需调整路由配置 `uri` 为 `lb://service-name`
- 调整日志/限流策略：修改 `application.yml` 的 `gateway.filters.*`，同步更新本指南
