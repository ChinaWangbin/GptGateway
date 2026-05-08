# GptGateway 项目说明

## 1. 项目结构

```text
GptGateway
|- pom.xml
|- gateway
|- demo-json-service
|- demo-stream-service
|- nacos-config
|  |- gateway-routes.yaml
|- docs
   |- nacos-config-checklist.md
```

## 2. 技术栈与版本

- Java: 17
- Spring Boot: 3.0.2
- Spring Cloud: 2022.0.0
- Spring Cloud Alibaba: 2022.0.0.0
- Nacos: 2.x（`http://localhost:8848/nacos`）

## 3. Nacos 命名空间与配置

- 命名空间名称：`modelGateway`
- 命名空间 ID：`modelGateway`

网关与 `demo-json-service` 使用 Nacos 命名空间 `modelGateway`。  
`demo-stream-service` 不注册到 Nacos，仅通过网关自定义路由转发。

在 Nacos 控制台（`modelGateway` 命名空间）创建配置：

- Data ID: `gateway-routes.yaml`
- Group: `GATEWAY_GROUP`
- 类型: `YAML`
- 内容: 复制 `nacos-config/gateway-routes.yaml`

## 4. 启动顺序

1. 启动 Nacos。
2. 在 Nacos 切换到命名空间 `modelGateway` 并导入 `gateway-routes.yaml`。
3. 构建项目：

```bash
mvn clean package -DskipTests
```

4. 启动三个应用：

```bash
mvn -pl demo-json-service spring-boot:run
mvn -pl demo-stream-service spring-boot:run
mvn -pl gateway spring-boot:run
```

## 5. 双路由模式验证

### 5.1 模式一：Nacos 自定义路由（SSE）

`demo-stream-service` 通过 Nacos 路由配置转发，不依赖服务注册。  
网关地址：

```text
http://localhost:8080/stream-api/stream/ticks?count=5
```

测试：

```bash
curl -N "http://localhost:8080/stream-api/stream/ticks?count=5"
```

### 5.2 模式二：Nacos 服务发现自动路由（JSON）

`demo-json-service` 注册到 Nacos，网关自动发现并路由。  
网关地址：

```text
http://localhost:8080/demo-json-service/api/hello
```

测试：

```bash
curl "http://localhost:8080/demo-json-service/api/hello"
```

## 6. 直连地址（可选）

- JSON 服务：`http://localhost:18081/api/hello`
- SSE 服务：`http://localhost:18082/stream/ticks?count=5`

## 7. 常见排查

- SSE 路由不生效：检查 Nacos 中 `gateway-routes.yaml` 是否在 `modelGateway` 命名空间。
- 自动路由不生效：检查 `demo-json-service` 是否注册到 `modelGateway`。
- SSE 非流式输出：使用 `curl -N`，并检查路径是否为 `/stream-api/...`。
