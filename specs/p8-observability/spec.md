# Spec：OPTIMIZE Roadmap P8 — 可观测性落地

> 状态：待评审 → 待执行
> 关联：`OPTIMIZE.md` 第 8 章 Roadmap 阶段五（P8 新增）、`README.md` §15.18.1、`AGENTS.md` §5.6 / §10
> 前置结论：Roadmap P6（P2-1~P2-6）与 P7（P3-1~P3-6）已全部闭环并各有“处理进展”，本 Spec **不重做**。

## 1. 目标与范围

在不改变任何领域行为、状态机与事务边界的前提下，为全部 10 个可运行进程补齐三类可观测能力：

1. **指标 + 健康探针（P8-1/P8-2/P8-3）**：Prometheus 指标暴露、liveness/readiness 探针、核心交易业务计数器。
2. **日志治理（P8-4）**：JSON 日志固定 `app` + `traceId` + `spanId` 字段，杜绝 MDC 误带敏感信息。
3. **追踪完善（P8-5）**：核对 `/internal/**` 5xx 在 span 上的状态语义，明确 Feign 客户端观测口径。

**非目标**：不改业务逻辑、不改 API/DB/MQ 契约、不引入 Grafana/Prometheus 服务端编排、不做链路采样策略调整、不重构现有日志切面。

## 2. 现状事实（已核实）

- 10 个进程（2 BFF + gateway + 7 领域服务）的 `management` 配置**仅含** `tracing.sampling` 与 `zipkin.tracing.endpoint`；全仓库无 `micrometer-registry-prometheus`，无 `management.endpoint.health.group`，无 `management.metrics.tags`。
- 观测依赖落点：`mall-remote` 声明 actuator / tracing / zipkin / feign-micrometer / feign-hc5 / logstash；`mall-infra` 依赖 `mall-remote`；两个 BFF 经 `mall-core → mall-remote` 传递；`mall-gateway` 与 `mall-product-service` 各自独立声明同类依赖。
- 依赖矩阵：`user-service`=db+redis；`cart-service`=db；`product-service`=db；`order/payment/refund/bill-service`=db+redis+rabbit；两个 BFF 与 gateway 无 DB/Redis/MQ。
- 业务埋点候选（现有装配点，无需新增抽象）：
  - `LocalMessageServiceImpl.publishOne/scheduleRetry`（`mall-infra`）——Outbox 投递结果。
  - `CheckoutTxService.createOrder`（`mall-order-service`）——下单落单结果。
  - `PaymentSuccessServiceImpl`（`mall-payment-service`）——成交事实写入结果。
  - `RefundInfoServiceImpl.createRefundApplication`（`mall-refund-service`）——退款受理结果。
  - `InventoryServiceImpl.reserveForOrder`（`mall-product-service`）——库存预占结果。
- `logback-spring.xml` 共 3 份：`mall-remote`、`mall-gateway`、`mall-product-service`，内容一致；JSON 已含 `app`，MDC 的 `traceId/spanId` 由 encoder 默认输出。
- 全仓库无手工 `MDC.put`；按现有日志语句未发现打印 password/secret/privateKey 的位置（`prod` 由 `ProductionSecretGuard` 兜底）。
- 测试影响面：`InventoryServiceImplTest`（3 参构造）、`PaymentSuccessServiceImplTest`（6 参构造）会因构造参数新增而编译失败，其余测试不直接构造目标类。

## 3. 方案（决策完整）

### 3.1 P8-1 指标暴露

- `backend/mall-remote/pom.xml`、`backend/mall-gateway/pom.xml`、`backend/mall-product-service/pom.xml` 各新增：
  ```xml
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
  </dependency>
  ```
  版本由父 POM 的 `spring-boot-dependencies` 管理，不新增版本属性。
- 10 份 `application.yml` 的 `management` 块统一补：
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,prometheus
    metrics:
      tags:
        application: ${spring.application.name}
  ```
  `application` 公共 tag 让 `mall_http_server_requests_seconds_count{application="mall-order-service"}` 可直接区分进程，替代在各处手工加 tag。

### 3.2 P8-2 健康探针

10 份 `application.yml` 统一补：
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,<按进程追加依赖>
```

readiness 追加规则（只列该进程真实存在的健康指示器，避免组状态恒为 `UNKNOWN`）：

| 进程 | readiness include |
|---|---|
| `mall-user-bff` / `mall-admin-bff` / `mall-gateway` | `readinessState` |
| `mall-user-service` | `readinessState,db,redis` |
| `mall-cart-service` / `mall-product-service` | `readinessState,db` |
| `mall-order-service` / `mall-payment-service` / `mall-refund-service` / `mall-bill-service` | `readinessState,db,redis,rabbit` |

### 3.3 P8-3 业务指标（直接注入 `MeterRegistry`，不新建共享抽象）

规则：只增加计数器，不加 Timer/长耗时逻辑；计数器名与标签固定如下（单位：次）。

| 计数器 | 标签 | 埋点位置 |
|---|---|---|
| `mall.outbox.publish` | `result=ack\|retry\|failed` | `LocalMessageServiceImpl`：`whenComplete` 分支 ack→`ack`；`scheduleRetry` 未超限→`retry`；超限置 FAILED→`failed` |
| `mall.order.checkout` | `result=success\|failure` | `CheckoutTxService.createOrder`：`transactionTemplate.execute` 正常返回→`success`；抛出→`failure` 后原样重抛 |
| `mall.payment.deal` | `result=success\|duplicate` | `PaymentSuccessServiceImpl`：`paymentDealRepository.insert` 返回 true→`success`；另一笔真实支付发冲正→`duplicate` |
| `mall.refund.apply` | `result=success\|failure` | `RefundInfoServiceImpl.createRefundApplication`：事务返回→`success`；抛出→`failure` 后原样重抛 |
| `mall.inventory.reserve` | `result=success\|insufficient\|duplicate` | `InventoryServiceImpl.reserveForOrder`：整单完成→`success`；`reserveStock==0`→`insufficient` 后抛；`DuplicateKeyException` 跳过→`duplicate`（每次跳过计一次） |

实现约束：
- 全部通过构造注入 `io.micrometer.core.instrument.MeterRegistry`，字段声明为 `private final`，沿用现有显式构造器风格。
- 计数器用 `Counter.builder(name).tag("result", value).register(meterRegistry).increment()` 直接创建；Micrometer 自带同标签幂等，无需本地缓存。
- **不改**异常类型、异常消息、事务传播、锁与 CAS 顺序；埋点只做旁路计数。

### 3.4 P8-4 日志治理

3 份 `logback-spring.xml` 的 `LogstashEncoder` 内新增：
```xml
<includeMdcKeyName>traceId</includeMdcKeyName>
<includeMdcKeyName>spanId</includeMdcKeyName>
```
将 JSON 的 MDC 字段收敛为 `traceId`/`spanId` 两项，避免未来任意 MDC 键把用户/凭据等敏感值带进 ES；`app` 继续由 `customFields` 提供。控制台 pattern 不变。三份文件修改必须完全一致。

### 3.5 P8-5 追踪完善

- 核对并记录：`GlobalExceptionHandler` 对 `/internal/**` 的 `code >= 500` 已返回 HTTP 500（P3-1 落地），`ErrorDecoder` 与 `feign-micrometer` 能将其识别为失败 span——本项**只写文档，不改代码**。
- Feign 观测口径写入 README §15.18.1：`feign-micrometer` 已随 `mall-remote` 生效，`spring.cloud.openfeign.client.config.<service>` 的连接/读超时即 span 内的调用预算；慢调用以 Zipkin 中 `httpclient` span 的 `duration` 判别，不新增本地埋点。
- 不新增采样率、baggage 或自定义 Observation。

## 4. 影响面与回滚

- **兼容性**：仅新增依赖与配置键；`/actuator/*` 属新增端点，不影响既有 `/api/**` 与 `/internal/**`。Prometheus 端点默认走 actuator 端口（未单独配置 `management.server.port`，与现网一致）。
- **安全**：`/actuator/prometheus` 与 `/actuator/health` 由进程端口直接暴露，生产需由网关/网络策略隔离（与当前 `/actuator/health` 一致，非本次新增风险面）。不做认证改造。
- **性能**：Micrometer 计数器为内存自增，无网络与 DB 开销；Prometheus 拉取为独立 HTTP 请求。
- **回滚**：删除新增依赖与 `management.endpoint/metrics` 配置即可；业务计数器为旁路代码，回滚对应注入与 `increment()` 不涉及数据迁移。

## 5. 验收标准

1. `cd backend; mvn clean test`（JDK 21）全绿，测试总数在现有 74 条基础上不减少。
2. `mvn -o dependency:tree` 可见 3 个新增 pom 均含 `io.micrometer:micrometer-registry-prometheus`；两个 BFF 仍无 `com.dameng` / `org.redisson` / `spring-boot-starter-amqp` / `com.baomidou`。
3. 10 份 `application.yml` 均含 `prometheus` 暴露、`probes.enabled=true` 与 `metrics.tags.application`；readiness 组与该进程依赖矩阵一致。
4. 3 份 `logback-spring.xml` 内容一致且仅收敛 `traceId`/`spanId` 两个 MDC 键。
5. `OPTIMIZE.md` 新增 P8 章节（含验收标准与影响面）并在目录登记；`README.md` §15.18.1 与配置表同步。
6. 提交仅包含本 Spec 列出的文件，不包含工作区既有的 11 个无关脏文件。

## 6. 明确不做

- 不新增 `BusinessMetrics` 共享组件、AOP 切面或注解式埋点。
- 不修改 `mall-gateway` 路由、CORS、BFF 无状态化边界。
- 不调整 Outbox、锁、CAS、MQ 拓扑与任何领域不变量。
- 不引入 Prometheus/Grafana 容器编排改动。
