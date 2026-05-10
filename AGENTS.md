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

- `gateway`：网关主服务（Spring Cloud Gateway）
- `demo-json-service`：JSON 测试服务（注册到 Nacos）
- `demo-stream-service`：SSE 流式测试服务（不注册到 Nacos，走静态路由）

## Nacos 约定

- Nacos 地址：`http://localhost:8848/nacos`
- 账号密码：`nacos / nacos`
- 命名空间名称：`modelGateway`
- 命名空间 ID：`modelGateway`

## 路由模式（当前实现）

### 模式一：Nacos 配置中心自定义路由（SSE）

- 路由配置来源：Nacos 配置中心
- Data ID：`gateway-routes.yaml`
- Group：`GATEWAY_GROUP`
- Namespace：`modelGateway`
- 路由目标：`http://localhost:18082`（`demo-stream-service`）
- 网关路径：`/stream-api/**`

### 模式二：Nacos 服务发现自动路由（JSON）

- `demo-json-service` 注册到 Nacos（`modelGateway`）
- 网关开启 `discovery.locator.enabled=true`
- 访问路径：`/demo-json-service/**`

## 关键配置文件

- 根依赖管理：`pom.xml`
- 网关配置：`gateway/src/main/resources/application.yml`
- JSON 服务配置：`demo-json-service/src/main/resources/application.yml`
- SSE 服务配置：`demo-stream-service/src/main/resources/application.yml`
- Nacos 路由模板：`nacos-config/gateway-routes.yaml`

## 启动步骤

1. 启动 Nacos。
2. 在 Nacos `modelGateway` 命名空间导入 `gateway-routes.yaml`（Data ID：`gateway-routes.yaml`，Group：`GATEWAY_GROUP`）。
3. 在项目根目录执行：
   - `mvn clean package -DskipTests`
4. 分别启动服务：
   - `mvn -pl demo-json-service spring-boot:run`
   - `mvn -pl demo-stream-service spring-boot:run`
   - `mvn -pl gateway spring-boot:run`

## 接口验证

- 自动路由（JSON）：
  - `curl "http://localhost:8080/demo-json-service/api/hello"`
- 自定义路由（SSE）：
  - `curl -N "http://localhost:8080/stream-api/stream/ticks?count=5"`

## 维护说明

- 若调整命名空间，需同步修改：
  - `gateway` 的 `nacos.config.namespace` 与 `nacos.discovery.namespace`
  - `demo-json-service` 的 `nacos.discovery.namespace`
  - Nacos 配置发布命名空间
- `demo-stream-service` 当前设计为“非注册式服务”，如改为注册式，需要同步调整：
  - `demo-stream-service` 依赖与配置
  - `gateway-routes.yaml` 的 `uri`（静态地址改 `lb://service-name`）
