# Tasks：P8 可观测性落地

> 依据：`specs/p8-observability/spec.md`
> 执行约束：JDK 21 编译；每步只改 Spec 列出的文件；完成后 `mvn clean test` 全绿。

## T1 依赖：引入 Prometheus registry

- [x] `backend/mall-remote/pom.xml` 新增 `io.micrometer:micrometer-registry-prometheus`（无版本，由父 BOM 管理）
- [x] `backend/mall-gateway/pom.xml` 同上
- [x] `backend/mall-product-service/pom.xml` 同上
- 验证：`mvn -o -B -ntp -pl mall-remote,mall-gateway,mall-product-service -am dependency:tree` 中出现该 artifact

## T2 配置：10 份 application.yml 的 management 扩展

逐文件（共 10 份）：

- [x] `mall-user-bff`：endpoints 暴露、metrics 公共 tag、probes，readiness=`readinessState`
- [x] `mall-admin-bff`：同上，readiness=`readinessState`
- [x] `mall-gateway`：同上（保留既有 `endpoints.web.exposure.include` 的并集），readiness=`readinessState`
- [x] `mall-user-service`：readiness=`readinessState,db,redis`
- [x] `mall-cart-service`：readiness=`readinessState,db`
- [x] `mall-product-service`：readiness=`readinessState,db`
- [x] `mall-order-service`：readiness=`readinessState,db,redis,rabbit`
- [x] `mall-payment-service`：readiness=`readinessState,db,redis,rabbit`
- [x] `mall-refund-service`：readiness=`readinessState,db,redis,rabbit`
- [x] `mall-bill-service`：readiness=`readinessState,db,redis,rabbit`

统一新增片段：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState[,db][,redis][,rabbit]
  metrics:
    tags:
      application: ${spring.application.name}
```
- [x] 验证：`Grep 'prometheus' → 10 个文件命中`；`Grep 'probes' → 10 个文件命中`

## T3 业务指标：Outbox 投递（mall-infra）

- [x] `LocalMessageServiceImpl` 构造注入 `MeterRegistry`，新增计数器 `mall.outbox.publish{result}`
- [x] `whenComplete` 收到 ack → `result=ack`；`scheduleRetry` 未超限 → `result=retry`；超限置 FAILED → `result=failed`
- 验证：`LocalMessageSendJobTest` 等既有测试编译通过；`mvn -pl mall-infra test`

## T4 业务指标：下单（mall-order-service）

- [x] `CheckoutTxService` 构造注入 `MeterRegistry`，计数器 `mall.order.checkout{result}`
- [x] `transactionTemplate.execute` 正常返回 → `success`；异常 → `failure` 后原样重抛（不改异常类型/消息）
- 验证：`mvn -pl mall-order-service test`

## T5 业务指标：支付成交（mall-payment-service）

- [x] `PaymentSuccessServiceImpl` 构造注入 `MeterRegistry`，计数器 `mall.payment.deal{result}`
- [x] `insert` 返回 true → `success`；另一笔真实支付冲正分支 → `duplicate`
- [x] 同步更新 `PaymentSuccessServiceImplTest` 构造调用，传入 `new SimpleMeterRegistry()`
- 验证：`mvn -pl mall-payment-service test`（4 条既有断言仍绿）

## T6 业务指标：退款受理（mall-refund-service）

- [x] `RefundInfoServiceImpl` 构造注入 `MeterRegistry`，计数器 `mall.refund.apply{result}`
- [x] `transactionTemplate.execute` 正常返回 → `success`；异常 → `failure` 后原样重抛
- 验证：`mvn -pl mall-refund-service test`（`RefundPolicyTest`、`RefundDomainArchitectureTest` 不受影响）

## T7 业务指标：库存预占（mall-product-service）

- [x] `InventoryServiceImpl` 构造注入 `MeterRegistry`，计数器 `mall.inventory.reserve{result}`
- [x] 整单完成 → `success`；`reserveStock==0` → `insufficient` 后抛；`DuplicateKeyException` 跳过 → `duplicate`
- [x] 同步更新 `InventoryServiceImplTest` 构造调用，传入 `new SimpleMeterRegistry()`
- 验证：`mvn -pl mall-product-service test`（13 条既有断言仍绿）

## T8 日志治理

- [x] `mall-remote/src/main/resources/logback-spring.xml` 的 `LogstashEncoder` 增加 `includeMdcKeyName`（`traceId`、`spanId`）
- [x] `mall-gateway`、`mall-product-service` 同名文件同步为完全一致内容
- 验证：三份文件 diff 为空；`Grep 'includeMdcKeyName' → 3 文件 × 2 行`

## T9 文档同步

- [x] `OPTIMIZE.md` 目录新增 `P8` 条目；正文新增第 9 章「P8：可观测性」，含目标、5 个子项落地方式、验收标准、影响面与回滚
- [x] `README.md` §15.18.1 补充：Prometheus 指标暴露与公共 `application` tag、liveness/readiness 分组、业务计数器清单、日志 MDC 收敛、Feign/`/internal` 5xx span 口径
- [x] `README.md` §15.18 配置表新增 `management.endpoints.web.exposure.include`、`management.endpoint.health.probes.enabled`、`management.metrics.tags.application` 三行
- 验证：文档描述与 T2–T8 的实际配置/代码逐项一致

## T10 全量回归与提交

- [x] 设置 `JAVA_HOME=D:\develop\java\jdk21.0.11_10` 后执行 `mvn -o -B -ntp -T 1C clean test`
- [x] 确认测试总数 ≥ 74 且全绿
- [ ] `git status` 仅包含本 Spec 涉及文件；11 个既有无关脏文件保持未暂存
- [ ] 提交（提交信息不含 Agent/模型署名），推送 `origin`
