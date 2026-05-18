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

`demo-stream-service` 通过 Nacos 路由配置转发，不依赖服务注册。网关启动后会监听 `gateway-routes.yaml`，修改 Nacos 配置即可动态新增、修改或删除自定义路由，无需重启网关。  
网关地址：

```text
http://localhost:8080/stream-api/stream/ticks?count=5
```

测试：

```bash
curl -N "http://localhost:8080/stream-api/stream/ticks?count=5"
```

动态刷新验证：

1. 在 Nacos 中把 `Path=/stream-api/**` 改为 `Path=/sse-api/**` 并发布。
2. 不重启网关，访问 `curl -N "http://localhost:8080/sse-api/stream/ticks?count=5"` 应生效。
3. 再访问旧路径 `/stream-api/...`，应不再命中该自定义路由。
4. 删除 `spring.cloud.gateway.routes` 下的路由并发布，自定义路由应失效；服务发现自动路由不受影响。

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

## 8. 开发热更新

三个模块均已加入 `spring-boot-devtools`：

- `gateway`
- `demo-json-service`
- `demo-stream-service`

开发时仍按模块分别启动：

```bash
mvn -pl demo-json-service spring-boot:run
mvn -pl demo-stream-service spring-boot:run
mvn -pl gateway spring-boot:run
```

DevTools 监听的是编译后的 `target/classes`。修改 Java 代码后，需要 IDE 或 Maven 触发编译，DevTools 才会自动重启对应模块并加载新代码。

IntelliJ IDEA 建议开启：

- `Settings > Build, Execution, Deployment > Compiler > Build project automatically`
- `Settings > Advanced Settings > Allow auto-make to start even if developed application is currently running`

能力边界：

- 修改 Controller 方法内容：自动快速重启后生效。
- 新增 Controller、新增接口、新增 Bean：自动快速重启后生效。
- 修改 `application.yml`：自动快速重启后生效。
- 修改 Nacos 中的 `gateway-routes.yaml`：网关监听配置变更并刷新路由缓存，无需本地重启。
- 修改 `pom.xml` 或新增依赖：通常需要停止并重新执行 `spring-boot:run`。

注意：DevTools 是“自动快速重启”，不是 JVM 原地热替换。若要求新增 Controller/Bean 时进程完全不重启，需要使用 JRebel 或 DCEVM + HotswapAgent。
