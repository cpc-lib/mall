# 电商商城平台（Mall）全量代码审计与优化方案（OPTIMIZE.md）

> **文档说明**：本文档基于对仓库全量代码（`backend/` 19 个 Maven 模块、`backend/env/sql/dm8/schema.sql`、`user-ui/`、`admin-ui/`、`.github/workflows/ci.yml` 及配套运维文档）的深度静态审计生成。所有优化项均严格遵循 [AGENTS.md](AGENTS.md) 中的领域不变量（库存四桶、支付成交唯一性、退款防超退、双向账账核对、Redisson Watchdog + Fence Token 围栏 CAS、MANUAL ACK + Failure DLQ），并精确标注了**文件路径、代码行号、根因分析与落地方案**。

---

## 目录

- [1. 架构现状评估与优化全景矩阵](#1-架构现状评估与优化全景矩阵)
- [2. P0：高危安全漏洞与交易正确性缺陷（建议立即修复）](#2-p0高危安全漏洞与交易正确性缺陷建议立即修复)
- [3. P1：领域不变量、分布式锁围栏与最终一致性优化](#3-p1领域不变量分布式锁围栏与最终一致性优化)
- [4. P2：数据库 Schema、长事务、N+1 RPC 与性能瓶颈优化](#4-p2数据库-schema长事务n1-rpc-与性能瓶颈优化)
- [5. P3：微服务边界、Feign 治理、依赖与配置优化](#5-p3微服务边界feign-治理依赖与配置优化)
- [6. P4：前端工程（`user-ui` & `admin-ui`）交互、性能与代码治理](#6-p4前端工程user-ui--admin-ui交互性能与代码治理)
- [7. P5：测试覆盖率、ArchUnit 架构守护与 CI/CD 流水线优化](#7-p5测试覆盖率archunit-架构守护与-cicd-流水线优化)
- [8. 建议落地路线图（Roadmap）](#8-建议落地路线图roadmap)

---

## 1. 架构现状评估与优化全景矩阵

本项目在交易领域建模上具备良好的工程基础：划分了 8 个限界上下文（`shared / product / order / payment / refund / user / cart / bill`），设计了库存四桶恒等式、`1:N` 支付尝试与 `t_payment_deal` 唯一成交事实、`RefundPolicy` 退款额度冻结、Redisson 分布式锁 + `fence_token` 围栏 CAS、Transactional Outbox + RabbitMQ Failure DLQ / Parking Lot 以及微信账单双向逐笔核对。

但在**从单体（Monolith）向微服务（P3–P8）演进拆分的过程**中，跨进程边界、事务范围、锁粒度、鉴权拦截、SQL 索引及前端状态管理仍留存大量可优化空间。

| 优先级 | 类别 | 核心问题数 | 关键收益 |
|---|---|---|---|
| **P0** | 安全漏洞与交易正确性 Bug | 9 项 | 堵住匿名调用支付/退款/账单接口与全量订单越权泄露；消除 Watchdog 永久死锁、库存并发双扣/双补、异常支付冲正重试丢失、超时 `PAYING` 订单漏关等资金/库存风险 |
| **P1** | 分布式锁围栏、不变量与一致性 | 8 项 | 打通跨服务 Feign `fence_token` 透传；统一同一聚合根锁 Key；补齐关单前查成交事实与未发货取消补库存 CAS 守卫；消除 Outbox 跨进程竞态重投 |
| **P2** | 数据库、长事务、N+1 与内存性能 | 6 大类 | 剥离 `@Transactional` 内的跨服务 RPC 与公网 HTTP；消除退款列表/下单/对账等 N+1 查询；将 10MB 账单解析内存峰值从 1GB+ 降至几十 MB；补齐 7 个高频索引 |
| **P3** | 微服务依赖、Feign 与配置治理 | 6 大类 | 修复 HTTP 200 绕过 Feign `ErrorDecoder` 与污染链路追踪指标的问题；剥离 API 契约模块的 AMQP/DB 传递依赖；让 BFF 彻底无状态化 |
| **P4** | 前端工程（`user-ui` / `admin-ui`） | 5 大类 | 消除 `admin-ui` 首屏 3.83MB Luckysheet 同步阻塞；修复 `user-ui` 跨页扫码轮询失效、支付宝弹窗拦截、第 101+ 笔订单退款不可编辑与路由死循环 |
| **P5** | 测试体系、ArchUnit 与 CI/CD | 4 项 | 为 16 个零测试模块补齐核心领域单测；将 ArchUnit 扩展至全业务上下文；CI 构建提速 40%+ |

---

## 2. P0：高危安全漏洞与交易正确性缺陷（建议立即修复）

### P0-1：`AuthInterceptor.isPublic()` 默认放行黑名单逻辑缺陷，导致 `/api/wx-pay/**`、`/api/ali-pay/**` 非回调接口全部免登录裸奔

- **定位**：
  - `backend/mall-common/src/main/java/cc/ivera/shared/security/AuthInterceptor.java:56-66`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/interfaces/WxPayController.java:93-155`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/interfaces/AliPayController.java:156-195`
  - `backend/mall-refund-service/src/main/java/cc/ivera/refund/interfaces/RefundApplicationController.java:28-55`
  - `backend/mall-refund-service/src/main/java/cc/ivera/refund/interfaces/PaymentRefundQueryController.java:42-53`
- **现状与问题**：
  ```java
  if (uri.startsWith("/api/auth/") || uri.startsWith("/api/cart") || uri.startsWith("/api/checkout")
      || uri.startsWith("/api/refund-applies") || uri.startsWith("/api/admin/")
      || uri.startsWith("/api/order") || uri.startsWith("/api/user/"))
      return false;
  return !uri.startsWith("/api/payment-app") && !uri.startsWith("/api/payment-channel")
      && !uri.startsWith("/api/payment-config") && !uri.startsWith("/api/reconciliation")
      && !uri.startsWith("/api/refund-info");
  ```
  `isPublic()` 最后一行采用“黑名单取反”逻辑。由于 `/api/wx-pay/**`、`/api/wx-pay-v2/**`、`/api/ali-pay/**` 既不在第 63 行的拦截列表，也不在第 65 行的黑名单中，**这三个前缀下的所有接口全部被判定为 `isPublic() == true`**！
- **影响**：
  任何未登录的匿名攻击者均可直接经网关（`:8080`）调用：
  1. `POST /api/wx-pay/cancel/{orderNo}`、`POST /api/ali-pay/trade/close/{orderNo}`：恶意关闭任意用户的待支付订单；
  2. `POST /api/wx-pay/refunds`、`POST /api/ali-pay/trade/refund`、`POST /api/wx-pay/refunds/{orderNo}/{reason}`：免登录对任意订单发起 V1 兼容退款申请（且 `RefundInfoServiceImpl.createRefundApplication` 不经过 V2 三层退款额度冻结）；
  3. `GET /api/wx-pay/querybill/{billDate}/{type}`、`GET /api/wx-pay/downloadbill/{billDate}/{type}`、`GET /api/ali-pay/bill/downloadurl/query/{billDate}/{type}`：**免登录下载商户全量微信/支付宝交易账单与资金账单**；
  4. `GET /api/wx-pay/query/{orderNo}`、`GET /api/ali-pay/trade/query/{orderNo}`、`GET /api/wx-pay/query-refund/{refundNo}`：探测任意订单/退款单的渠道原始报文。
- **优化方案**：
  將 `AuthInterceptor.isPublic()` 改为严格的**白名单机制**（默认返回 `false`），仅放行明确声明的公开路径（登录/注册/刷新/申请重置密码、商品分页与详情、行政区划树、支付/退款渠道异步回调 `/api/wx-pay/native/notify`、`/api/wx-pay-v2/native/notify`、`/api/wx-pay/refunds/notify`、`/api/ali-pay/trade/notify` 及 Swagger 文档路径）；同时对账单下载、渠道退款查询等管理接口补充 `AuthContext.requireAdmin()` 校验。

---

### P0-2：`OrderInfoController` 水平越权（IDOR）与全库用户订单隐私泄露

- **定位**：
  - `backend/mall-order-service/src/main/java/cc/ivera/order/interfaces/OrderInfoController.java:33-60`
  - `backend/mall-order-service/src/main/java/cc/ivera/order/application/impl/OrderInfoServiceImpl.java:171-173`
- **现状与问题**：
  - `GET /api/order-info/list`（第 34 行）仅要求登录，未校验 `ROLE_ADMIN`，也未按 `AuthContext.userId()` 过滤，直接调用 `orderInfoService.listOrderByCreateTimeDesc()` 执行 `SELECT * FROM t_order_info ORDER BY create_time DESC`，返回**全平台所有用户的全部历史订单**（含收货人姓名、手机号、详细地址、订单号、金额）。
  - `GET /api/order-info/query-order-status/{orderNo}`（第 50 行）未校验订单归属人（`order.getUserId().equals(AuthContext.userId())`）。
- **优化方案**：
  - 若 `/api/order-info/list` 仅供管理端排障，增加 `AuthContext.requireAdmin()` 并改为服务端分页；若供普通用户使用，必须强制过滤 `user_id = AuthContext.requireLogin().getUserId()`。
  - 在 `query-order-status/{orderNo}` 及支付发起接口（`WxPayController.nativePayByOrderNo`、`AliPayController.tradePagePayByOrderNo`）中校验当前登录用户是否为订单拥有者（或管理员）。

---

### P0-3：`RedissonDistributedLockTemplate` 在 `nextFenceToken()` 异常时导致 Watchdog 锁永久死锁泄漏

- **定位**：`backend/mall-infra/src/main/java/cc/ivera/shared/infrastructure/lock/RedissonDistributedLockTemplate.java:58-92`
- **现状与问题**：
  ```java
  locked = lock.tryLock(waitTimeMillis, -1, TimeUnit.MILLISECONDS);
  if (!locked) { ... }
  // 全局单调递增围栏令牌
  long fenceToken = nextFenceToken();
  context = FenceTokenHolder.acquire(lockKey, fenceToken);
  ...
  } finally {
      if (context != null) {
          long heldMillis = context.heldMillis();
          FenceTokenHolder.release();
          if (locked && lock.isHeldByCurrentThread()) {
              lock.unlock();
          }
          ...
      }
  }
  ```
  第 60 行通过 `leaseTime = -1` 成功获取了带 Redisson Watchdog 自动续期的分布式锁（`locked = true`）。随后第 69 行调用 `nextFenceToken()`（内部发起 3 次 Redis 命令）。如果此时第 69 行因网络抖动抛出 `RedisTimeoutException` 或其他异常，第 70 行 `context = FenceTokenHolder.acquire(...)` 不会执行，`context` 保持为 `null`。进入 `finally` 块后，因 `if (context != null)` 为假，**`lock.unlock()` 被直接跳过**！而 Redisson Watchdog 后台定时任务会每 10 秒自动给该锁续期，导致该 `lockKey` **在当前 JVM 存活期间永久死锁**。
- **优化方案**：
  在 `finally` 块中将 `FenceTokenHolder.release()` 与 `lock.unlock()` 解耦，确保只要 `locked == true` 就一定释放锁：
  ```java
  } finally {
      long heldMillis = 0L;
      long token = 0L;
      if (context != null) {
          heldMillis = context.heldMillis();
          token = context.getFenceToken();
          FenceTokenHolder.release();
      }
      if (locked && lock.isHeldByCurrentThread()) {
          try {
              lock.unlock();
          } catch (Exception unlockEx) {
              log.warn("Failed to unlock key={}", lockKey, unlockEx);
          }
      }
      if (context != null) {
          if (holdWarnMillis > 0 && heldMillis > holdWarnMillis) {
              log.warn("Distributed lock held too long, key={}, fenceToken={}, heldMs={}ms", lockKey, token, heldMillis);
          }
          lockAuditRecorder.record(LockAuditEvent.of(LockAuditEvent.TYPE_RELEASED, lockKey, token, heldMillis));
      }
  }
  ```

---

### P0-4：`InventoryServiceImpl` 先查 `countByBizNo`、后改库存、最后吞掉 `DuplicateKeyException` 的 TOCTOU 并发双扣/双补漏洞

- **定位**：`backend/mall-product-service/src/main/java/cc/ivera/product/application/impl/InventoryServiceImpl.java:110-256`
- **现状与问题**：
  在 `convertToSoldOnReceipt`（第 122–135 行）、`restockForRefund`（第 181–199 行）和 `writeOffLostForRefund`（第 209–228 行）中，执行顺序为：
  1. `if (transactionRepository.countByBizNo(bizNo) > 0) continue;`（普通 `SELECT COUNT(*)`，无锁）；
  2. 执行 `t_product` 库存四桶条件 UPDATE（如 `releaseSoldStock`、`commitSoldStock`、`writeOffLostStock`）；
  3. 调用 `insertTransaction(...)`（第 237–256 行）写入 `t_inventory_transaction`，并在第 252 行 `catch (DuplicateKeyException ignore)` **直接吞掉唯一键冲突异常**！
- **影响**：
  当两个并发请求（例如用户手动确认收货与 `AutoReceiptScheduler` 定时任务，或退款并发重试）携带相同 `bizNo` 同时到达 `mall-product-service` 时，两个线程可同时通过第 1 步 `countByBizNo(bizNo) == 0`，随后**两个线程都执行第 2 步 `UPDATE t_product` 扣减/回补库存**；后提交流水的线程在第 3 步触发 `uk_inventory_biz_no` 唯一键冲突，但异常被 `insertTransaction` 吞掉，事务正常提交，造成**库存被双倍回补或双倍结转，破坏四桶恒等式**！
- **优化方案**：
  反转执行顺序或让唯一键冲突回滚事务：在同一个本地事务内**先插入 `t_inventory_transaction`**（依赖 `uk_inventory_biz_no` 数据库唯一索引做原子抢占），若捕获 `DuplicateKeyException` 则直接幂等 `continue`（不执行 `t_product` 更新）；只有流水插入成功才执行 `t_product` 条件 UPDATE（若条件 UPDATE 影响行数为 0 则抛 `BizException` 触发整个事务连同流水一起回滚）。

---

### P0-5：`ExceptionRefundServiceImpl` 重复/晚到支付自动冲正重试短路 Bug（渠道失败后重试误判幂等直接丢弃）

- **定位**：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/ExceptionRefundServiceImpl.java:114-164`
- **现状与问题**：
  ```java
  PaymentOrder paymentOrder = paymentOrderQueryApi.findByPaymentNo(paymentNo);
  ...
  Integer refundable = paymentOrder.refundableAmount();
  if (refundable == null || refundable <= 0) {
      log.info("支付单无可冲正余额，幂等返回, paymentNo={}", paymentNo);
      return null;
  }
  RefundOrder refundOrder = tx.execute(status -> {
      RefundOrder existing = refundOrderRepository.findByPaymentNoAndApplyType(paymentNo, applyType);
      if (existing != null) { return existing; }
      boolean frozen = paymentOrderCommandApi.freezeChannelRefund(paymentNo, refundable);
      ...
  });
  executeChannelRefund(order, refund, paymentNo);
  ```
  当重复支付（`DUPLICATE_PAYMENT`）或晚到支付（`LATE_PAYMENT`）事件第 1 次消费时：
  1. 第 134–149 行本地事务提交，并调用 `paymentOrderCommandApi.freezeChannelRefund(paymentNo, refundable)` 将支付单的 `refund_frozen_amount` 设为 `paid_amount`（此时 `paymentOrder.refundableAmount() = paidAmount - refundedAmount - refundFrozenAmount = 0`），同时落库 `t_refund_order`（状态 `APPROVED`）与 `t_refund_info`（状态 `PROCESSING`）。
  2. 第 155 行在事务外调用 `executeChannelRefund` 发起微信/支付宝渠道退款。若此时渠道网络超时或返回异常，方法抛异常触发 RabbitMQ Consumer 的 Spring Retry 第 2 次重试（或管理员调用 `trigger` 重试）。
  3. **第 2 次重试进入方法时**，第 126 行在查询 `findByPaymentNoAndApplyType` **之前**先计算 `paymentOrder.refundableAmount()`。因为第 1 次已经冻结了全部金额，此时 `refundable` 为 `0`！代码在第 129 行直接打印 `"支付单无可冲正余额，幂等返回"` 并返回 `null`，消费者随即 `basicAck` 确认消息！
- **影响**：
  一旦首次渠道退款调用遇到任何瞬态异常，该笔重复支付或晚到支付冲正单将永远停留在未成功状态，且 MQ 消息被正常 ACK 吞掉，**资金无法自动原路退回**。
- **优化方案**：
  进入 `doExceptionPaymentRefund` 时，**先查询 `refundOrderRepository.findByPaymentNoAndApplyType(paymentNo, applyType)`**：
  - 若已存在且状态为 `SUCCESS`，直接幂等返回；
  - 若已存在但状态不是 `SUCCESS`（例如首次渠道调用失败留下的 `APPROVED / REFUNDING / FAILED` 记录），直接跳过冻结步骤，复用已有 `RefundOrder` / `RefundInfo` 继续执行 `executeChannelRefund`；
  - 仅当 `existing == null` 时，才校验 `paymentOrder.refundableAmount() > 0` 并执行 `freezeChannelRefund`。

---

### P0-6：`TimeoutOrderCloseScheduler` 漏扫所有 `pay_status = 'PAYING'` 的超时未付订单

- **定位**：
  - `backend/mall-order-service/src/main/java/cc/ivera/order/infrastructure/persistence/repository/OrderRepositoryImpl.java:139-158`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/PaymentOrderServiceImpl.java:69`
- **现状与问题**：
  当用户点击微信扫码支付或支付宝去支付时，`PaymentOrderServiceImpl.markPaying` 会调用 `orderStatusCommandApi.markPaying(orderNo)` 将 `t_order_info.pay_status` 从 `'UNPAID'` 更新为 `'PAYING'`（此时 `order_status` 仍为 `'WAIT_PAY'`，`legacy_status` 仍为 `'未支付'`）。
  但 DB 兜底定时任务 `TimeoutOrderCloseScheduler` 调用的 `OrderRepositoryImpl.listTimeoutNotPay(Date cutoff)` 查询条件写死为：
  ```java
  .eq(OrderInfoPO::getLegacyStatus, OrderStatus.NOTPAY.getType())
  .eq(OrderInfoPO::getPayStatus, PayStatus.UNPAID.getType())
  ```
- **影响**：
  只要用户打开过收银台生成过支付二维码（`pay_status = 'PAYING'`）但最终未付款，一旦 RabbitMQ 延迟关单消息丢失或消费失败进入 Failure Queue，`TimeoutOrderCloseScheduler` **永远扫不到这笔订单**！该订单将永久停留在 `WAIT_PAY + PAYING` 状态，其预占的 `locked_stock` 库存也永远无法释放。
- **优化方案**：
  将 `OrderRepositoryImpl.listTimeoutNotPay` 中的 `.eq(OrderInfoPO::getPayStatus, PayStatus.UNPAID.getType())` 改为 `.in(OrderInfoPO::getPayStatus, PayStatus.UNPAID.getType(), PayStatus.PAYING.getType())`（并加上 `.eq(OrderInfoPO::getOrderStatus, OrderLifecycleStatus.WAIT_PAY.getCode())`）。

---

### P0-7：`OrderNoUtils` 使用静态非线程安全 `SimpleDateFormat` 与高碰撞 4 位随机数

- **定位**：`backend/mall-common/src/main/java/cc/ivera/shared/infrastructure/util/OrderNoUtils.java:15-16, 68-72`
- **现状与问题**：
  ```java
  private static final Random RANDOM = new Random();
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
  public static String getNo() {
      String timestamp = DATE_FORMAT.format(new Date());
      int random = RANDOM.nextInt(10000);
      return timestamp + String.format("%04d", random);
  }
  ```
  1. **线程安全缺陷**：`SimpleDateFormat` 内部共享 `Calendar` 实例，多线程并发调用 `getOrderNo()` / `getPaymentNo()` / `getRefundNo()` / `getReservationNo()` / `getShipmentNo()` 时会出现时间戳错乱甚至抛出 `ArrayIndexOutOfBoundsException` / `NumberFormatException`。
  2. **高碰撞率**：秒级时间戳 + 4 位随机数每秒仅有 10,000 个空间；根据生日悖论，同一秒内并发产生约 118 个单号时碰撞概率即达 50%，直接触发数据库唯一键异常导致下单或支付失败。
- **优化方案**：
  使用不可变且线程安全的 `DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")`（毫秒级）配合 `AtomicInteger` 单调序列号 + `ThreadLocalRandom`：
  ```java
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.of("Asia/Shanghai"));
  private static final AtomicInteger SEQ = new AtomicInteger(0);

  public static String getNo() {
      String ts = FORMATTER.format(Instant.now());
      int seq = (SEQ.getAndIncrement() & 0x7FFFFFFF) % 1000;
      int rnd = ThreadLocalRandom.current().nextInt(100);
      return ts + String.format("%03d%02d", seq, rnd);
  }
  ```

---

### P0-8：`HttpClientUtils` 禁用 SSL 证书校验（MITM 风险）且每次新建无超时的 `CloseableHttpClient`

- **定位**：`backend/mall-payment-service/src/main/java/cc/ivera/payment/infrastructure/util/HttpClientUtils.java:121-143`
- **现状与问题**：
  ```java
  SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, (chain, authType) -> true).build();
  ```
  1. `(chain, authType) -> true` 无条件信任所有证书，使微信支付 V2 统一下单（`WxPayOrderService.java:465`）与退款查询（`WxPayRefundService.java:255`）暴露于中间人攻击（MITM）之下。
  2. 每次请求都新建并关闭 `CloseableHttpClient`，且未设置 `RequestConfig`（`connectTimeout` / `socketTimeout` / `connectionRequestTimeout`），若远端挂起将无限阻塞业务线程与分布式锁。
- **优化方案**：
  移除自定义的 trust-all `SSLContext`，使用 JVM 默认受信 CA 证书库；复用带 `PoolingHttpClientConnectionManager` 和明确超时配置（如连接 3000ms、读取 10000ms）的单例 `CloseableHttpClient`。

---

### P0-9：微服务内部契约 `/internal/**` 零鉴权暴露，且支付渠道接口明文输出商户私钥

- **定位**：
  - `backend/mall-common/src/main/java/cc/ivera/shared/infrastructure/config/WebMvcAuthConfig.java:17-19`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/interfaces/PaymentChannelController.java:37-56`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/interfaces/PaymentInternalController.java:160-193`
- **现状与问题**：
  1. 尽管 `ProductInternalController.java:21` 注释写有“由内部 Token 认证”，但 `WebMvcAuthConfig` 仅拦截 `/api/**`，所有服务的 `/internal/**` 端口（`8083`–`8089`）没有任何内部 Token 或签名校验。内网任意容器/主机只要能访问对应端口，即可直接调用 `/internal/user/auth/admin/reset-password` 重置任意管理员密码、调用 `/internal/payment/config/apps` 导出微信/支付宝商户私钥、或调用 `/internal/payment/success/offline-paid/{orderNo}` 将任意订单标记为已支付。
  2. `GET /api/payment-channel/list-all`、`/list`、`/{id}` 直接将 `PaymentChannel` 实体序列化返回前端，HTTP 响应报文中包含明文 `privateKey`、`apiV3Key`、`partnerKey`、`merchantPrivateKey`。
- **优化方案**：
  1. 在 `InternalFeignConfiguration` 中通过 `RequestInterceptor` 自动注入内部调用令牌请求头（如 `X-Internal-Token: ${INTERNAL_AUTH_TOKEN}`），并在 `WebMvcAuthConfig` 中为 `/internal/**` 注册 `InternalTokenInterceptor` 进行常量时间比对（`MessageDigest.isEqual`）。
  2. 在 `PaymentChannelController` 返回前端 VO 时对 `privateKey`、`apiV3Key`、`partnerKey`、`merchantPrivateKey` 进行掩码脱敏（如 `"******"`），更新时若前端传入掩码值则保留数据库原值。

---

## 3. P1：领域不变量、分布式锁围栏与最终一致性优化

### P1-1：`FenceTokenHolder` 仅限进程内 `ThreadLocal`，跨服务 Feign 调用丢失围栏令牌

- **定位**：
  - `backend/mall-infra/src/main/java/cc/ivera/shared/domain/lock/FenceTokenHolder.java:13-42`
  - `backend/mall-infra/src/main/java/cc/ivera/infra/feign/InternalFeignConfiguration.java:21-51`
  - `backend/mall-order-service/src/main/java/cc/ivera/order/infrastructure/persistence/repository/OrderRepositoryImpl.java:301-307`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/infrastructure/persistence/repository/PaymentOrderRepositoryImpl.java:205-211`
- **现状与问题**：
  `AGENTS.md` §5.6 规定核心聚合表（`t_order_info`、`t_payment_order`、`t_refund_order`、`t_refund_info`、`t_bill_import`）在持锁推进状态时必须携带 `SET fence_token = #{fenceToken}` 与 `WHERE ... AND fence_token <= #{fenceToken}`。
  然而微服务物理拆分后，`FenceTokenHolder` 依然只是单 JVM 内的 `ThreadLocal`：
  - 当 `mall-payment-service` 或 `mall-refund-service` 持锁并通过 OpenFeign 调用 `mall-order-service`（`/internal/order/**`）修改 `t_order_info` 时，`mall-order-service` 线程中的 `FenceTokenHolder.get()` 恒为 `null`！
  - 当 `mall-order-service` 或 `mall-refund-service` 持锁并通过 OpenFeign 调用 `mall-payment-service`（`/internal/payment/**`）修改 `t_payment_order` 时，`mall-payment-service` 线程中的 `FenceTokenHolder.get()` 也恒为 `null`！
  - 由于 Mapper XML 中配置了 `<if test="fenceToken != null">AND fence_token &lt;= #{fenceToken}</if>`，**所有跨服务的 `t_order_info` 和 `t_payment_order` 状态推进实际上全部跳过了围栏 CAS 校验**。
- **优化方案**：
  1. 在 `InternalFeignConfiguration` 中注册 `RequestInterceptor`：当 `FenceTokenHolder.get() != null` 时，向 `/internal/**` 请求注入 Header `X-Fence-Token: <token>`。
  2. 在 `mall-infra` 中提供 `FenceTokenServletFilter`（仅作用于 `/internal/**` 且需通过内部鉴权）：若当前线程未持本地锁且请求头包含合法的 `X-Fence-Token`，则在请求生命周期内绑定至 `FenceTokenHolder`，并在 `finally` 中释放。

---

### P1-2：同一聚合根（`orderNo`）的分布式锁 Key 命名碎片化，导致并发操作互不互斥且引发 `fence_token` 逆序误拒

- **定位**：
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/wxpay/WxPayOrderService.java:156, 222, 256, 351, 366`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/interfaces/WxPayV2Controller.java:156`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/AliPayServiceImpl.java:138, 187, 325, 375`
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/PaymentSuccessServiceImpl.java:65`
- **现状与问题**：
  针对同一个 `orderNo` 的支付推进与关单操作，代码中定义了 **11 种互不相同的分布式锁 Key 前缀**：
  - `payment:wx:native:v3:order:{orderNo}`
  - `payment:wx:notify:pay:{orderNo}`
  - `payment:wx:v2:notify:pay:{orderNo}`
  - `payment:wx:query:order:{orderNo}`
  - `payment:wx:check:order:{orderNo}`
  - `payment:order:cancel:{orderNo}`
  - `payment:ali:pagepay:order:{orderNo}`
  - `payment:ali:notify:pay:{orderNo}`
  - `payment:ali:check:order:{orderNo}`
  - `payment:ali:query:order:{orderNo}`
  - `payment:offline:mark:{orderNo}`
- **影响**：
  1. **并发互不互斥**：当微信/支付宝异步回调（`notify:pay:{orderNo}`）与前端主动轮询查单（`query:order:{orderNo}`）或超时关单反查（`check:order:{orderNo}`）同时到达时，因为锁 Key 不同，两个线程会**同时进入临界区并发写同一笔 `t_payment_order`**。
  2. **围栏令牌逆序误杀**：由于 `lock:fence:seq` 是全局自增的，线程 A（拿 `notify` 锁，获 `token=100`）和线程 B（拿 `query` 锁，获 `token=101`）同时运行时，若线程 B 先执行了一次 UPDATE 将行上的 `fence_token` 写为 `101`，线程 A 紧接着执行的合法 UPDATE 会因为 `101 <= 100` 不成立而被数据库静默拒绝！
- **优化方案**：
  按聚合根统一锁命名空间：
  - 同一订单的支付状态推进/查单/关单统一收敛到订单级支付锁：`payment:order:{orderNo}`；
  - 同一退款单的受理/签收/重试/渠道通知/状态同步统一收敛到退款单级锁：`refund:order:{refundNo}`（创建退款申请时锁定 `refund:create:{orderNo}`）。

---

### P1-3：多处核心状态推进缺失分布式锁或 CAS 条件（含锁外脏对象覆盖 Bug）

- **定位与问题明细**：
  1. **`RefundOrderServiceImpl.queryRefundStatus` 锁外脏对象覆盖 `successTime`**：
     - 文件：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundOrderServiceImpl.java:574-595`
     - 问题：第 574 行在无锁状态下读取 `RefundOrder refundOrder`（此时 `status=REFUNDING, successTime=null`）；第 578 行调用 `refundApplicationService.queryRefundStatus(refundNo)`（其内部加锁并发布 `RefundSucceededEvent` 触发 `settle(refundNo)`，在数据库里将 `t_refund_order` 更新为 `status=SUCCESS, success_time=now`）。然而回到 `queryRefundStatus` 的第 590–592 行后，代码在**锁外**修改了第 574 行读出的旧内存对象 `refundOrder.setStatus("SUCCESS")` 并调用 `refundOrderRepository.update(refundOrder)`——由于旧对象的 `successTime` 为 `null`，若更新字段包含或覆盖其他列，或与并发回调交错，极易产生状态回退或脏写。
  2. **`RefundQuotaReleasedListener.release` 无状态 CAS 条件更新**：
     - 文件：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/event/RefundQuotaReleasedListener.java:19-28`
     - 问题：调用 `refundOrderRepository.update(apply)` 仅按 `WHERE id = ?` 更新 `status = FAILED`，未限定当前状态 `WHERE id = ? AND status IN ('APPROVED', 'REFUNDING')`。
  3. **`AliPayServiceImpl.cancelOrder` 与 `checkOrderStatus` 关单分支未加分布式锁**：
     - 文件：`backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/AliPayServiceImpl.java:240-251, 302-315`
  4. **`PaymentSucceededConsumer`、`OrderCloseConsumer`、`AdminOrderShipmentController.forceClose` 更新订单未加锁**：
     - 文件：`backend/mall-order-service/src/main/java/cc/ivera/order/interfaces/mq/PaymentSucceededConsumer.java:68`、`OrderCloseConsumer.java:65`、`AdminOrderShipmentController.java:97`
  5. **`PasswordResetRequestServiceImpl.handle` 与 `reject` 无事务且无 `WHERE status = 'PENDING'` CAS 守卫**：
     - 文件：`backend/mall-user-service/src/main/java/cc/ivera/user/application/impl/PasswordResetRequestServiceImpl.java:67-98`
     - 问题：两个管理员并发点击“受理”会重置两次密码，且若 `requestRepository.update(row)` 失败，密码已改但申请单仍为 `PENDING`。
- **优化方案**：
  - 在 `RefundOrderServiceImpl.queryRefundStatus` 中移除第 590–592 行的锁外冗余更新（`RefundSucceededListener` / `RefundQuotaReleasedListener` 已在锁内完成了 `t_refund_order` 结算/失败回写），直接重新 `getRequired(refundNo)` 返回最新状态。
  - 为 `RefundQuotaReleasedListener` 和 `PasswordResetRequestServiceImpl` 补齐基于前置状态的 CAS 条件 UPDATE。
  - 为 `AliPayServiceImpl.cancelOrder` 及订单状态推进统一加锁。

---

### P1-4：4 处违反 `AGENTS.md` 核心领域不变量的代码路径

1. **未发货取消受理（`handleCancelBeforeShip`）漏调 `OrderRefundQuotaApi.addRestockedQty` CAS 守卫**
   - **定位**：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundOrderServiceImpl.java:504, 515-518`
   - **违规点**：违反 `AGENTS.md` §5.4（*“`order_item.restocked_qty` 的累加守卫属于退款调用方：退款受理时先调用 `OrderRefundQuotaApi.addRestockedQty` 做 CAS 守卫，再调用商品服务的回补/核销接口”*）。`handleCancelBeforeShip` 直接调用了 `refundInventoryGateway.restockForRefund(refundNo, toStockLines(itemsOf(refundNo)), false)`，而没有调用第 601 行的 `restockWithGuard(refundNo, false)`，导致 `t_order_item.restocked_qty` 未累加且失去了订单行级防超补 CAS 守卫。
   - **修复**：将 `handleCancelBeforeShip` 改为直接调用 `restockWithGuard(refundOrder.getRefundNo(), false)`。

2. **管理员强制关单与微信/支付宝取消订单前未查 `t_payment_deal` 成交事实**
   - **定位**：
     - `backend/mall-order-service/src/main/java/cc/ivera/order/interfaces/AdminOrderShipmentController.java:86-100`（`forceClose`）
     - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/wxpay/WxPayOrderService.java:257-274`（`cancelOrder`）
     - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/AliPayServiceImpl.java:240-251`（`cancelOrder`）
   - **违规点**：违反 `AGENTS.md` §5.3（*“关单 / 取消前必须先查成交事实，已成交则标记支付，禁止‘已收款却本地关单’”*）。由于 `t_order_info.pay_status` 是由 MQ 异步消费 `PAYMENT_SUCCEEDED` 更新的，在支付刚刚成交但 MQ 尚未消费的窗口期内，`order.getPayStatus()` 仍为 `UNPAID/PAYING`；此时 `forceClose` 或 `cancelOrder` 未查 `paymentOrderQueryApi.hasSuccessfulPayment(orderNo)` 就直接将订单 CAS 关闭并释放库存！
   - **修复**：与 `OrderInfoServiceImpl.closeTimeoutOrder`（`OrderInfoServiceImpl.java:251`）对齐，在 `forceClose` 和 `cancelOrder` 关单前必须先校验 `paymentOrderQueryApi.hasSuccessfulPayment(orderNo)`（或查渠道真实状态），若已成交则调用 `markPaid(orderNo)` 并拒绝关单。

3. **订单状态推进（`updateStatusByOrderNoIfStatus`）与远程库存预占提交/释放非原子且重试短路**
   - **定位**：`backend/mall-order-service/src/main/java/cc/ivera/order/application/impl/OrderInfoServiceImpl.java:185-238`
   - **问题**：第 197 行 `orderRepository.casLegacyStatus(...)` 先将 `t_order_info` 更新为 `SUCCESS` 或 `CLOSED/CANCEL` 并提交；随后第 200–208 行通过 Feign 调用 `inventoryService.commitReservation(orderNo)` 或 `inventoryService.releaseReservation(orderNo)`。一旦此时 `mall-product-service` 短暂超时抛异常，MQ 消费者（`PaymentSucceededConsumer` / `OrderCloseConsumer`）或 `TimeoutOrderCloseScheduler` 重试时，看到 `t_order_info` 已经是 `SUCCESS` 或 `CLOSED`，**直接提前返回不再调用库存接口**，导致 `t_inventory_reservation` 永久停留在 `LOCKED` 状态，`locked_stock` 永久泄漏！
   - **修复**：当 `PaymentSucceededConsumer`、`OrderCloseConsumer` 或 `TimeoutOrderCloseScheduler` 发现订单已处于 `PAID` / `CLOSED` 状态时，仍幂等调用一次 `inventoryService.commitReservation(orderNo)` 或 `releaseReservation(orderNo)`（商品侧本身基于 `reservation` 状态 CAS 和 `biz_no` 幂等，已提交/已释放会安全短路）。

4. **退款受理/创建中远程库存回补早于 `initiateChannelRefund`，后者异常时未补偿库存**
   - **定位**：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundOrderServiceImpl.java:290-298, 503-513, 551-557`
   - **问题**：在 `doCreate`、`prepareAccept`、`prepareConfirmReturn` 的本地事务中，先通过 Feign 调用了 `orderRefundQuotaApi.addRestockedQty` 和 `refundInventoryGateway.restockForRefund / writeOffLostForRefund`（在订单服务和商品服务已独立提交事务），紧接着调用 `initiateChannelRefund(refundOrder)`（内部远程调用 `paymentOrderCommandApi.freezeChannelRefund`）。若 `initiateChannelRefund` 失败抛出异常，`mall-refund-service` 本地事务回滚，但**商品服务已回补/核销的库存和订单服务已累加的 `restocked_qty` 没有回滚，导致库存虚增**。
   - **修复**：先执行 `paymentOrderCommandApi.freezeChannelRefund` 资金冻结，成功后再执行 `addRestockedQty` 与库存回补/核销；或者将资金冻结、库存结转纳入可重入的状态阶段，避免本地事务回滚导致跨服务状态悬空。

---

### P1-5：Seata Saga 下单状态机 `CreateOrder` 先于 `ReserveInventory` 提交可见订单，且失败补偿未清理延迟关单 Outbox

- **定位**：
  - `backend/mall-order-service/src/main/resources/statelang/order-checkout.json:7-37`
  - `backend/mall-order-service/src/main/java/cc/ivera/order/application/impl/CheckoutTxService.java:65-153`
- **现状与问题**：
  1. 在 `order-checkout.json` 中，第一步 `CreateOrder`（`CheckoutTxService.prepare`）在本地事务中直接写入了 `order_status = 'WAIT_PAY'` 的有效订单与 `ORDER_CLOSE` Outbox 消息并提交，第二步才调用 `ReserveInventory` 预占库存。在 `ReserveInventory` 尚未完成（或因库存不足正在执行反向补偿 `CancelOrder`）的时间窗口内，数据库中已存在一张可见的 `WAIT_PAY` 订单，与 `AGENTS.md` §5.1（*“库存不足必须让整个下单事务失败，不允许先生成有效订单后再补偿扣库存”*）冲突。
  2. 当 `ReserveInventory` 因库存不足失败触发 `CancelOrder`（`CheckoutTxService.cancelIfUnpaid`，第 149–152 行）时，仅将订单置为 `CLOSED`，**没有调用 `localMessageService.markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, orderNo)`**，导致 30 分钟后 RabbitMQ 仍会投递一条无意义的关单消息并触发渠道查单。
- **优化方案**：
  - 将 Saga 正向顺序调整为 **`ReserveInventory`（预占库存）→ `CreateOrder`（落单 + Outbox）→ `ClearCart`（清购物车）**（`orderNo` 已在 `CheckoutSagaService` 启动状态机前生成，`t_inventory_reservation` 按 `order_no + product_id` 唯一约束幂等预占），库存不足时第一步直接失败，根本不会写入 `t_order_info` 和 `t_local_message`。
  - 在 `CheckoutTxService.cancelIfUnpaid` 中补充 `localMessageService.markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, orderNo)`。

#### 处理进展（2026-11-23）

- ✅ 优化方案第 2 条已实现：`CheckoutTxService.cancelIfUnpaid` 在条件关单成功（rows > 0）后调用 `localMessageService.markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, orderNo)`，补偿关单不再残留 30 分钟后投递的延迟关单消息。
- ⏸ 优化方案第 1 条（`ReserveInventory` 前移）**暂缓，转为独立设计变更**，原因：
  - 原文前提「`t_inventory_reservation` 按 `order_no + product_id` 唯一约束」与实际 schema 不符：`backend/env/sql/dm8/schema.sql` 中 `order_item_id BIGINT NOT NULL`，唯一键为 `uk_reservation_order_item(order_item_id)`；`InventoryServiceImpl.reserveForOrder` 对 `orderItemId == null` 直接抛异常，而 `orderItemId` 仅在 `CreateOrder` 落单后产生，因此无法在不改预占模型的前提下前移。
  - 若前移，需同步处理：`schema.sql` 唯一键与可空性迁移；`InventoryService` 预占契约改为按「订单 + 商品」幂等；`CheckoutTxService.prepare` 的可用库存校验（预占后可用库存已扣减，原校验会误报「库存不足」）；Saga 入参需从购物车构造预占行；并需核实所有读取 `reservation.order_item_id` 的退款/结算路径。
  - 按 `AGENTS.md` §2/§7，这属于设计变更 + DB schema 变更，需先出「现状 → 目标 → 影响面 → 回滚」评审并同步 `README.md` 第 15 章，不在 P1 收尾阶段直接实施。

---

### P1-6：Transactional Outbox（`t_local_message`）跨进程竞态重投、`STATUS_SENT` 被误覆盖及 BFF 寄宿问题

- **定位**：
  - `backend/mall-infra/src/main/java/cc/ivera/shared/infrastructure/mq/LocalMessageServiceImpl.java:44-66, 103-170`
  - `backend/mall-infra/src/main/java/cc/ivera/shared/infrastructure/mq/LocalMessageSendJob.java:16-32`
  - `backend/mall-user-bff/src/main/java/cc/ivera/UserBffApplication.java:13-17`
- **现状与问题**：
  1. **跨进程立即重投竞态**：`saveAndPublishAfterCommit` 插入 `PENDING` 记录时将 `nextRetryTime` 设为 `new Date()`（当前时刻）。当 `mall-order-service` / `mall-payment-service` / `mall-refund-service` 的业务事务刚一提交，正在执行 `afterCommit() -> publishOne(message)` 时，`mall-user-bff` 中的 `LocalMessageSendJob` 恰好执行 `SELECT ... WHERE status = 'PENDING' AND next_retry_time <= #{now}`，会把这条刚提交的消息捞出来**并发重复投递**。
  2. **`scheduleRetry` 将已 `SENT` 的消息回退为 `PENDING / FAILED`**：第 155 行 `scheduleRetry` 的更新条件写成了 `.in(LocalMessage::getStatus, LocalMessage.STATUS_PENDING, LocalMessage.STATUS_SENT)`。若线程 A（`afterCommit`）已投递成功并将状态改为 `SENT`，线程 B（并发的 `LocalMessageSendJob`）投递超时触发 `scheduleRetry`，会把已经 `SENT` 的状态覆盖回 `PENDING` 甚至 `FAILED`！
  3. **`afterCommit()` 同步阻塞 HTTP 响应线程长达 5 秒**：第 128 行 `correlationData.getFuture().get(5000, TimeUnit.MILLISECONDS)` 在业务线程的 `afterCommit` 回调中同步阻塞等待 RabbitMQ Broker Confirm，且每次发送都经历 `JSON -> POJO -> JDK ObjectOutputStream` 二次序列化。
  4. **寄宿在 `mall-user-bff` 导致 BFF 重度耦合数据库与 MQ**：`mall-user-bff` 本身不产生任何 Outbox 消息，却为了跑 `LocalMessageSendJob` 而持有 DM8 连接池、MyBatis-Plus、Redisson 与 RabbitMQ 连接，且 `mall-user-bff` 的 classpath 下甚至没有 `mall-order-service` 的 `OrderCloseRabbitConfig` 队列声明。
- **优化方案**：
  1. **消除初次提交竞态**：在 `saveAndPublishAfterCommit` 插入 `PENDING` 记录时，将初始 `nextRetryTime` 设为 `new Date(System.currentTimeMillis() + 10_000L)`（预留 10 秒给本地 `afterCommit` 异步发布）。
  2. **修复状态覆盖**：将 `scheduleRetry`（第 155 行）的 `.in(..., STATUS_PENDING, STATUS_SENT)` 改为严格的 `.eq(LocalMessage::getStatus, LocalMessage.STATUS_PENDING)`。
  3. **异步 Confirm 回调**：使用 `correlationData.getFuture().whenCompleteAsync(...)` 替代同步 `.get(5000, ...)`，避免阻塞 HTTP 线程。
  4. **按领域归属扫描或加锁（中期演进）**：在 `LocalMessageSendJob.publishPendingMessages` 上增加非阻塞分布式锁防止多实例并发重投；后续可按 `biz_type` 过滤，由 `order / payment / refund` 各自扫描本领域的 Outbox 消息，让 `mall-user-bff` 与 `mall-admin-bff` 彻底成为无状态 BFF（同步更新 `AGENTS.md` 与 `README.md`）。

**处理进展（2026-11-23）**：第 1～3 条已完成；第 4 条已按领域拆分落地：

- `LocalMessageService.publishPending(List<String> bizTypes)`，Mapper 增加 `biz_type in (...)` 过滤（空列表=全部，保持兼容）。
- `LocalMessageSendJob` 读取 `local-message.relay-biz-types`，锁键改为 `scheduler:local-message-send:{spring.application.name}`，避免多进程共用单键时每轮只有一个进程能扫描、其它域消息饿死。
- 三域分别中继：`mall-order-service` → `ORDER_CLOSE,PAYMENT_LATE_ARRIVED`；`mall-refund-service` → `REFUND_SYNC`；`mall-payment-service` → `PAYMENT_SUCCEEDED,DUPLICATE_PAYMENT_DETECTED`。
- `mall-user-bff` / `mall-admin-bff` / `mall-bill-service` 设 `relay-enabled=false`；`mall-user-bff` 移除 `@EnableScheduling`。
- 新增 `mall-infra` 单测 `LocalMessageSendJobTest`（biz_type 过滤、按应用名隔离锁键、抢锁失败跳过）。
- 遗留：`mall-user-bff` 的 DM8/Redis 传递依赖来自 `mall-core`，真正无状态化需拆 `mall-core` 的 infra 依赖，另立重构项。

---

### P1-7：V2 退款主链路与异常支付冲正链路漏发 `REFUND_SYNC` 延迟状态同步消息

- **定位**：
  - `backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundOrderServiceImpl.java:644-673`
  - `backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/ExceptionRefundServiceImpl.java:231-246`
  - 对比 V1：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundApplicationServiceImpl.java:102`
- **现状与问题**：
  V1 `RefundApplicationServiceImpl.approve` 在调用渠道退款后会调用 `refundStatusSyncMessageService.sendRefundStatusSyncMessage(refundNo)` 发送 RabbitMQ 延迟同步消息。但在 V2 主力使用的 `RefundOrderServiceImpl.executeChannelRefund`（受理、未发货取消、退货签收、重试）与 `ExceptionRefundServiceImpl.executeChannelRefund`（重复支付/晚到支付冲正）中，**当渠道返回 `PROCESSING` 时从未调用 `sendRefundStatusSyncMessage(refundNo)`**！导致整套 `payment.refund.status-sync.*` RabbitMQ 延迟队列拓扑在 V2 链路中处于闲置状态，完全只能等待 `RefundStatusSyncScheduler` 定时轮询。
- **优化方案**：
  在 `RefundOrderServiceImpl.executeChannelRefund` 和 `ExceptionRefundServiceImpl.executeChannelRefund` 中，当渠道退款返回 `PROCESSING`（或发起成功后尚未终态）时，调用 `refundStatusSyncMessageService.sendRefundStatusSyncMessage(refundNo)`。

---

### P1-8：多实例定时任务惊群效应与 `catch (RuntimeException)` 降级无 `LIMIT` 全表扫描风险

- **定位**：
  - `backend/mall-order-service/src/main/java/cc/ivera/order/infrastructure/persistence/repository/OrderRepositoryImpl.java:149-156`
  - `backend/mall-refund-service/src/main/java/cc/ivera/refund/infrastructure/persistence/repository/RefundInfoRepositoryImpl.java:157-164`
  - 6 个 Scheduler：`TimeoutOrderCloseScheduler`、`AutoReceiptScheduler`、`MockLogisticsSimulationJob`、`OrderRequestCleanupScheduler`、`RefundStatusSyncScheduler`、`LocalMessageSendJob`
- **现状与问题**：
  1. 在 `OrderRepositoryImpl.listTimeoutNotPay` 和 `RefundInfoRepositoryImpl.listProcessingApproved` 中：
     ```java
     try {
         return orderInfoMapper.selectList(wrapper)...; // 带 .last("LIMIT " + safeBatch)
     } catch (RuntimeException dmLimitSyntax) {
         return orderInfoMapper.selectList(fallback)...; // 完全不带 LIMIT，且在内存中才 .limit(safeBatch)！
     }
     ```
     DM8 原生支持 `LIMIT N` 语法；这里的 `catch (RuntimeException)` 不仅毫无必要，而且一旦数据库发生慢查询超时或连接异常，会立即在 `catch` 块中发起第二次**不带 `LIMIT` 的全表查询**，瞬间拖垮数据库与 JVM 内存！
  2. 全部 6 个 `@Scheduled` 任务均未使用任务级非阻塞锁（如 `redissonClient.getLock("job:...").tryLock(0, -1, MILLISECONDS)`）。在微服务多实例部署下，所有节点会在同一时刻捞取相同的 Top 100/200 条记录并发争抢业务锁和外部支付渠道 API。
- **优化方案**：
  1. 立即移除 `OrderRepositoryImpl` 和 `RefundInfoRepositoryImpl` 中的 `catch (RuntimeException dmLimitSyntax)` 无 `LIMIT` 回退分支（或改用 MyBatis-Plus `Page<>(1, safeBatch, false)` 分页插件，天然兼容 DM8）。
  2. 为 6 个定时任务统一加上非阻塞任务锁（抢不到锁的实例直接跳过本轮调度）。

---

## 4. P2：数据库 Schema、长事务、N+1 RPC 与性能瓶颈优化

### P2-1：剥离 `@Transactional` / `TransactionTemplate` 内的跨服务 Feign RPC 与公网 HTTP/MinIO 调用

当前多个应用服务在开启本地数据库连接事务（占用 HikariCP 连接）期间，执行了多次跨服务 Feign 调用甚至公网微信/支付宝/MinIO 调用。高并发或下游抖动时极易耗尽数据库连接池（每個进程默认仅 10 个连接）：

| 所在服务 | 类与方法（行号） | 事务内包含的远程/慢 I/O 调用 | 优化方案 |
|---|---|---|---|
| `mall-order-service` | `CheckoutTxService.prepare` (`CheckoutTxService.java:65-134`) | 1 次 `cartService.selected` + 1 次 `paymentConfigLoader` + **$N$ 次 `productRepository.findById`** + 1 次 `addressService.getById` | 将购物车、支付配置、商品批量查询（`findByIds`）、收货地址查询全部移到 `@Transactional` 事务开启之前；事务内仅执行 `t_order_info`、`t_order_item` 批量插入与 `t_local_message` 插入 |
| `mall-payment-service` | `WxPayOrderService.cancelOrder` (`WxPayOrderService.java:261-273`)、`doSyncOrderStatusFromWxQuery` (`397-432`) | 事务内无任何本地表写入，却包裹了 `orderInfoService.getOrderByOrderNo`（Feign）、**`closeOrder`（公网微信 HTTPS）**、`orderStatusCommandApi.closeIfNotPay`（Feign） | 移除 `WxPayOrderService.cancelOrder` 和 `doSyncOrderStatusFromWxQuery` 外层无意义的 `transactionTemplate.execute` |
| `mall-payment-service` | `WxPayOrderService.doProcessOrderNotifyInTransaction` (`235`)、`AliPayServiceImpl` (`202`)、`WxPayV2Controller` (`236`) + `PaymentSuccessServiceImpl.handlePaymentSuccess` (`80`) | 同一个本地事务内**连续两次**通过 Feign 调用 `orderInfoService.getOrderByOrderNo(orderNo)` | 在进入事务前查询一次 `OrderInfo` 并作为参数传入 `handlePaymentSuccess`，消除事务内重复 Feign 调用 |
| `mall-product-service` | `StockImportServiceImpl.createImport` (`59-72`)、`saveImportFile` (`107-120`) | `@Transactional` 内调用 `fileStorage.save(...)` 上传 2MB 文件到 MinIO（且 `MinioStockImportFileStore.save` 每次都调 `bucketExists`） | 先完成文件存储（或先落库后上传，失败补偿），并将 `MinioStockImportFileStore.ensureBucket()` 移至 `@PostConstruct` 启动期执行一次 |
| `mall-refund-service` | `RefundOrderServiceImpl` (`doCreate:244-301`, `doUpdate:317-340`, `prepareAccept:489-513`, `doSettle:677-713`) | `tx.execute` 内多次调用 `orderQueryApi`、`paymentOrderQueryApi` 以及返回前的 `detail(refundOrder)`（再次 Feign 查订单） | 事务前预先读取 `OrderInfo` 与 `PaymentOrder`；将只读组装 `detail(refundOrder)` 移出 `tx.execute` 事务块外 |
| `mall-bill-service` | `BillReconcileServiceImpl.reconcileInTransaction` (`254-303`) | 单个大事务内发起 **6 次 Feign 调用**拉取全天支付账本与退款账本 | 在开启 `transactionTemplate` 前先通过 Feign 拉取平台支付/退款账本并完成内存双向比对，算出差异列表后，仅在短事务内批量落库差异单与更新批次状态 |

**处理进展（2026-11-23）**：已完成上述事务边界调整。下单在事务前批量加载商品并保留事务内幂等复查；支付回调以兼容默认方法新增 `OrderInfo` 入参重载，支付单 CAS、成交事实、支付信息与 Outbox 仍保持同事务；退款预读订单/支付单并外移响应组装与渠道调用，渠道调用仍持原分布式锁，库存与防超退顺序不变；对账仅在短事务中保存差异和更新批次；库存文件上传移出事务，MinIO bucket 改为启动时初始化。未改变 REST 路由、DB schema 或 MQ 拓扑，P1-5 Saga 顺序仍保持暂缓。JDK 21 下 `mvn -o clean test` 已通过。

---

### P2-2：消除高频 N+1 数据库查询与 N+1 跨服务 Feign RPC

1. **退款列表 `RefundOrderServiceImpl.details` 单页触发 $N$ 次 Feign + $2N$ 次 SQL**
   - **定位**：`backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundOrderServiceImpl.java:219, 234, 241, 883-910`
   - **问题**：用户端/管理端分页查询退款列表（单页最高 100 条）时，`details(List<RefundOrder> list)` 循环调用 `detail(refundOrder)`：
     - 每条退款单单独执行 1 次 `SELECT * FROM t_refund_item WHERE refund_no = ?`（$N$ 次 SQL）；
     - 每条退款单单独发起 1 次 Feign 调用 `orderQueryApi.findByOrderNo(orderNo)` 到 `mall-order-service`（**$N$ 次串行 HTTP 请求**）；
     - 每条失败退款单再查 1 次 `t_refund_info`。
   - **优化方案**：
     在 `details(List<RefundOrder> list)` 中批量化：
     1. 提取 `refundNos` 集合，通过 `SELECT * FROM t_refund_item WHERE refund_no IN (...)` 一次性查出所有退款明细并按 `refundNo` 分组；
     2. 提取去重后的 `orderNos` 集合，在 `OrderQueryApi` 新增 `findByOrderNos(Collection<String> orderNos)` 批量接口，**1 次 Feign 调用**取回全部关联订单；
     3. 提取 `FAILED` 状态的 `refundNos`，1 次 `IN` 查询取回 `t_refund_info`。

2. **下单准备 `CheckoutTxService.prepare` 重复 N+1 Feign 查商品**
   - **定位**：`backend/mall-order-service/src/main/java/cc/ivera/order/application/impl/CheckoutTxService.java:71, 81-89`
   - **问题**：第 71 行 `cartService.selected(userId)` 已经在购物车服务中批量查过一次商品；紧接着第 81–89 行又循环对每个购物车条目调用 `productRepository.findById(cart.getProductId())` 发起 $N$ 次单条 Feign 请求。
   - **优化方案**：提取 `selected` 中的 `productIds` 集合，调用一次 `productRepository.findByIds(productIds)` 批量获取最新商品快照。

3. **购物车 `CartServiceImpl.selected` 与 `clearSelected` 冗余 Feign 调用与 N+1 删除**
   - **定位**：`backend/mall-cart-service/src/main/java/cc/ivera/cart/application/impl/CartServiceImpl.java:100-111`
   - **问题**：
     - `selected(userId)` 调用 `list(userId)` 加载了用户购物车**全部**商品（含未勾选），向商品服务发起 Feign 查询后才在内存中过滤 `selected == true`。
     - `clearSelected(userId)` 先调 `selected(userId)`（白白发起一次跨服务商品查询），然后循环调用 `remove(userId, productId)` 执行 $N$ 次单行 `DELETE`。
   - **优化方案**：
     - 在 `CartRepository` 中新增 `findSelectedByUserId(userId)`（`WHERE user_id = ? AND (selected = '1' OR selected IS NULL)`）和 `deleteSelectedByUserId(userId)`（单条 `DELETE FROM t_cart_item WHERE user_id = ? AND (selected = '1' OR selected IS NULL)`，无需调用商品服务）。

4. **管理端批量调库存 `AdminProductController.batchAdjustStock` 串行 $N \sim 2N$ 次 Feign 调用**
   - **定位**：
     - `backend/mall-admin-bff/src/main/java/cc/ivera/product/interfaces/AdminProductController.java:93-122`
     - `backend/mall-product-service/src/main/java/cc/ivera/product/application/impl/ProductStockServiceImpl.java:38-47`
   - **问题**：`mall-admin-bff` 在 `for` 循环中逐条通过 Feign 调用 `mall-product-service` 的 `adjustOne`（失败时再调一次 `recordFailedAdjustment`）；且 `adjustOne` 内部对同一商品调用了两次 `productRepository.findById(productId)`。
   - **优化方案**：将批量循环下沉到 `mall-product-service` 内部（提供 `/internal/product/stock/batch-adjust`），BFF 仅需 1 次 Feign 调用；`adjustOne` 在 `adjustAvailable` 成功后直接用 `before.getAvailableStock() + delta` 计算 `afterStock`，省去第二次 `findById`。

5. **支付配置缓存 `PaymentConfigLoader.getDefaultAppConfigByChannelCode` 排序比较器内查库击穿缓存**
   - **定位**：`backend/mall-payment-service/src/main/java/cc/ivera/payment/infrastructure/config/PaymentConfigLoader.java:115, 190-205`
   - **问题**：尽管 `PaymentConfigLoader` 维护了内存 `appConfigCache`，但 `getDefaultAppConfigByChannelCode` 在 `.sorted((left, right) -> ...)` 比较器内部每次都执行 `paymentAppRepository.findById(left.getAppId())` 查数据库读取 `sortOrder`！导致每次按渠道获取默认支付配置都会触发多次数据库查询。
   - **优化方案**：在 `PaymentAppConfig` 模型中增加 `Integer sortOrder` 字段，加载缓存时一并填入，排序时直接比较内存字段；同时在 `loadApps()` 中直接复用 `channelCache` 而非逐个调用 `paymentChannelRepository.findById`。

**处理进展（2026-11-24）**：第 1～5 条已完成。退款列表改为「明细 1 次 IN + 订单 1 次批量 Feign + 失败单 1 次 IN」；下单准备已在 P2-1 改为 `findByIds` 批量取商品；购物车新增 `findSelectedByUserId` / `deleteSelectedByUserId` 单条 SQL，`selected` 为 NULL 仍视为已勾选；管理端批量调库存下沉为 `/internal/product/stock/batch-adjust` 一次 Feign，商品服务逐条独立事务，`adjustOne` 用调整前快照计算库存省去二次查询；支付配置缓存补 `sortOrder` 并复用渠道缓存，默认应用选择不再查库。REST 响应结构、失败流水语义与库存/退款不变量均未改变，JDK 21 下全量 `mvn -o clean test` 通过。

---

### P2-3：对账服务 `mall-bill-service` 大文件解析内存峰值、逐行插入与全量内存汇总优化

- **定位**：
  - `backend/mall-bill-service/src/main/java/cc/ivera/bill/application/parser/WxTradeBillParser.java:74-119`
  - `backend/mall-bill-service/src/main/java/cc/ivera/bill/application/impl/BillReconcileServiceImpl.java:203-210, 289-294, 317-318, 432, 514, 634-698`
- **现状与问题**：
  1. **DOM 解析内存爆炸**：`uploadBill` 允许上传最大 10MB 的 `.xlsx` 账单。`WxTradeBillParser.parseXlsx` 使用 `new XSSFWorkbook(new ByteArrayInputStream(content))` 将整份工作簿构建为内存 XML DOM 树并创建 `FormulaEvaluator`，10MB 的 Excel（约 5~10 万行流水）会瞬时分配 **500MB ~ 1.5GB 堆内存**，极易引发 Full GC 或 `OutOfMemoryError`。
  2. **逐行单条 `INSERT`**：`BillReconcileServiceImpl` 第 203–210 行和第 289–294 行在 `for` 循环中逐条调用 `billRecordRepository.save(record)` 和 `discrepancyRepository.save(discrepancy)`。2 万行账单会产生 2 万次独立 JDBC 往返。
  3. **未分批的超大 `IN (...)` 查询**：第 317–318、432、514 行将全天账单中所有 `billOrderNos`、`billSerialNos`、`billRefundNos` 一次性塞入单个 `IN (...)` 子句跨 Feign 传给 `mall-payment-service` 和 `mall-refund-service`。当单日流水超过数千笔时，会触发数据库 `IN` 列表参数上限或导致索引失效。
  4. **`getSummary` 全量拉取 CLOB 到内存做加总**：第 634–698 行在查询对账汇总时，把该批次所有 `t_bill_record`（含 `raw_line` CLOB 大字段）、所有 `t_bill_reconcile_discrepancy` 以及远程全天 `PaymentOrder` / `RefundOrder` 全部加载到 JVM 内存中用 Stream 求和。
- **优化方案**：
  1. 使用 Apache POI 事件驱动的 SAX 流式读取（`OPCPackage` + `XSSFReader` + `ReadOnlySharedStringsTable` + `XSSFSheetXMLHandler`）替代 `XSSFWorkbook`，将解析内存占用降低 90% 以上。
  2. 为 `BillRecordRepository` 和 `BillReconcileDiscrepancyRepository` 增加每批 500~1000 条的批量插入（MyBatis `<foreach>` 批量插入或 `SqlSession` `Batch` 模式）。
  3. 对跨服务 `listByChannelAndOrderNos`、`listByChannelAndChannelOrderNos`、`listByPaymentNos`、`listByRefundNos` 的参数集合按每批 500 个 ID 分片（Chunking）查询后合并。
  4. 在 `BillRecordMapper.xml` 和 `BillReconcileDiscrepancyMapper.xml` 中编写 `SELECT record_type, COUNT(*), COALESCE(SUM(total_amount),0), COALESCE(SUM(refund_amount),0) FROM t_bill_record WHERE import_id = #{importId} GROUP BY record_type` 聚合 SQL，避免在 `getSummary` 中加载全量流水与 `raw_line` CLOB。

**处理进展（2026-11-24）**：第 1～4 条已完成。`WxTradeBillParser.parseXlsx` 改为 `OPCPackage + XSSFReader + XSSFSheetXMLHandler` 纯 SAX 事件驱动，按单元格引用还原列位并保持日期单元格 `yyyy-MM-dd HH:mm:ss` 口径；批次流水与差异单改为单条多值 `INSERT`、每 500 条一批，主键回调不再逐行依赖；`PaymentLedgerRemoteAdapter` / `RefundLedgerRemoteAdapter` 的四个集合查询按 500 个单号分片后合并；新增 `BillRecordMapper.summarizeByImport` 按 `record_type` 在库内汇总笔数与金额（退款只统计 `SUCCESS/REFUND/REVOKED`），`getSummary` 的差异总数与 OPEN 数改为 `COUNT(*)`，对账比对查询也不再读取 `raw_line` CLOB。对账口径、批次状态与差异类型均未改变，JDK 21 下全量 `mvn -o clean test` 通过。

---

### P2-4：无界全表查询与动态 SQL 拼接治理

- **定位与问题**：
  1. `RefundInfoRepositoryImpl.listAllCreateTimeDesc`（`backend/mall-refund-service/src/main/java/cc/ivera/refund/infrastructure/persistence/repository/RefundInfoRepositoryImpl.java:57-61`）：执行 `SELECT * FROM t_refund_info ORDER BY create_time DESC`，无分页且包含 `content_return` / `content_notify` 两个 CLOB 大字段。
  2. `OrderShipmentRepositoryImpl.listShippedBefore` 与 `listInTransitBefore`（`backend/mall-order-service/src/main/java/cc/ivera/order/infrastructure/persistence/repository/OrderShipmentRepositoryImpl.java:53-67`）：每 15 秒由 `MockLogisticsSimulationJob` 调用，未加 `LIMIT`。
  3. `InventoryTransactionRepositoryImpl.recentByProduct`（`backend/mall-product-service/src/main/java/cc/ivera/product/infrastructure/persistence/repository/InventoryTransactionRepositoryImpl.java:47-53`）：直接拼接 `.last("LIMIT " + limit)`，虽 `limit` 为 `int`，但未做上界裁剪（如调用方传 `Integer.MAX_VALUE` 将拉取全表）。
- **优化方案**：
  - 为 `RefundInfoController.list` 改造为服务端分页（或至少限制 `LIMIT 200`）；
  - 为 `listShippedBefore` 和 `listInTransitBefore` 增加 `LIMIT 200` 批处理上限；
  - 在 `recentByProduct` 中对 `limit` 做 `int safeLimit = Math.min(Math.max(limit, 1), 200)` 边界收敛。

**处理进展（2026-11-24）**：三条均已落地。`RefundInfoRepositoryImpl.listAllCreateTimeDesc` 增加 `LIMIT 200`，并同步仓储端口注释；`OrderShipmentRepositoryImpl.listShippedBefore` / `listInTransitBefore` 增加 `LIMIT 200` 单批上限，物流模拟任务仍按 15 秒周期继续推进剩余运单；`InventoryTransactionRepositoryImpl.recentByProduct` 将 `limit` 收敛到 `1..200` 后再拼接方言 SQL。仅调整查询边界，未改变状态推进与库存口径。

---

### P2-5：Redis 与锁路径高频冗余 RTT 及阻塞式 `KEYS` 命令优化

1. **单次分布式锁加锁/释放产生 12 次同步 Redis 往返（RTT）**
   - **定位**：
     - `backend/mall-infra/src/main/java/cc/ivera/shared/infrastructure/lock/RedissonDistributedLockTemplate.java:102-108`
     - `backend/mall-infra/src/main/java/cc/ivera/shared/infrastructure/lock/RedisLockAuditRecorder.java:39-58`
     - `backend/mall-infra/src/main/java/cc/ivera/shared/infrastructure/lock/DistributedLockInterceptor.java:31, 75`
   - **问题**：
     - `nextFenceToken()` 每次加锁执行 3 次同步 Redis 命令：`counter.compareAndSet(0L, now)` → `counter.incrementAndGet()` → `counter.expire(Duration.ofDays(30))`；
     - `lockAuditRecorder.record(...)` 在加锁和释放时各执行 3 次同步 Redis 命令：`stream.add(...)` → `stream.trimNonStrict(...)` → `stream.expire(...)`（共 6 次）；
     - 再加上 `tryLock`（1 次）、`isHeldByCurrentThread`（1 次 `HEXISTS`）、`unlock`（1 次），**一次业务加锁周期竟高达 12 次同步 Redis 网络往返**！
     - 此外 `DistributedLockInterceptor` 在每次方法调用时都执行 `expressionParser.parseExpression(expression)` 重新解析 SpEL AST。
   - **优化方案**：
     - 将 `nextFenceToken()` 合并为 1 条 Lua 脚本（或启动时初始化一次初值，运行时仅执行 1 次 `INCR`）；
     - 在 `RedisLockAuditRecorder` 中使用 `StreamAddArgs.entries(fields).trimNonStrict().maxLen(MAX_LENGTH)` 将 `XADD` 与 `MAXLEN ~ 1000` 合并为单条命令，并通过 `RBatch` 管道化或异步执行；
     - 用 `lock.isHeldByThread(Thread.currentThread().getId())` 本地线程号判定替代远程 `isHeldByCurrentThread()`；
     - 在 `DistributedLockInterceptor` 中使用 `ConcurrentHashMap<String, Expression>` 缓存编译后的 SpEL 表达式。

2. **`UserAdminServiceImpl` 使用 $O(N)$ 阻塞式 `redisTemplate.keys("auth:refresh:*")` 判断单用户在线状态**
   - **定位**：`backend/mall-user-service/src/main/java/cc/ivera/user/application/impl/UserAdminServiceImpl.java:54, 91-112`
   - **问题**：
     - 查看单个用户详情 `detail(Long id)`（第 54 行）竟然调用了 `scanOnlineUserIds()`，在 Redis 中执行全库阻塞扫描 `KEYS auth:refresh:*` 并 `MGET` 所有在线 Token，只为了判断 `.contains(user.getId())`！
     - 此外，管理员禁用用户 `setStatus`（第 70–71 行）时仅递增了 `auth:token_version:{uid}`，未调用 `authSessionRepository.deleteAll(user.getId())` 清理该用户的 `auth:session:*` 与 `auth:refresh:*` 键。
   - **优化方案**：
     - `RedisAuthSessionRepository` 已经维护了每个用户的会话集合 `auth:user_sessions:{uid}`！判断单用户是否在线只需调用 `!authSessionRepository.listByUser(userId).isEmpty()`（$O(1)$ 复杂度）；列表页判断当页 10~20 个用户是否在线，仅需对该页的 `userId` 集合做一次 Pipeline `SCARD auth:user_sessions:{uid}`，彻底移除 `redisTemplate.keys("auth:refresh:*")`！
     - 在 `UserAdminServiceImpl.setStatus` 禁用用户时，同步调用 `authSessionRepository.deleteAll(user.getId())`。

3. **每个鉴权请求在 `AuthServiceImpl.verifyAccessToken` 中的冗余 Redis `SETNX` 及 Lua 脚本重复实例化**
   - **定位**：
     - `backend/mall-user-service/src/main/java/cc/ivera/user/application/impl/AuthServiceImpl.java:165-173, 212-217`
     - `backend/mall-user-service/src/main/java/cc/ivera/user/infrastructure/redis/RedisAuthSessionRepository.java:86-96, 123-128`
   - **问题**：
     - `verifyAccessToken` 在每次 HTTP 请求时都调用 `ensureVersion(userId)`，先发一次 `SETNX auth:token_version:{uid} 0`，再发一次 `GET auth:token_version:{uid}`。实际上当 key 不存在时默认版本号即为 `0L`，直接 `GET`（`null` 视为 `0L`）即可省去 50% 的 Redis 请求！
     - `RedisAuthSessionRepository.consumeRefresh` 每次调用都 `new DefaultRedisScript<>()`，导致 Spring Data Redis 每次重新计算脚本 SHA1；应提取为 `private static final DefaultRedisScript<String> CONSUME_REFRESH_SCRIPT` 常量。
     - `RegionServiceImpl.listAllOrdered`（`RegionServiceImpl.java:19-21`）与 `RegionController.tree`（`RegionController.java:30-54`）每次请求都从 DM8 全表拉取 ~3,420 条静态省市区记录并跨 Feign 传输重建树；应增加内存缓存（`volatile List<Map<String, Object>> cachedTree`）。

**处理进展（2026-11-24）**：三类均已落地。

1. 锁路径 RTT 收敛：`RedissonDistributedLockTemplate.nextFenceToken` 改为单条 Lua（`SET lock:fence:seq 毫秒初值 NX` + `EXPIRE 30 天` + `INCR`），由 3 次命令降为 1 次 RTT；`RedisLockAuditRecorder` 用 `RBatch` 把 `XADD`（`.trimNonStrict().maxLen(1000).noLimit()`）与 TTL 管道化为 1 次 RTT，替代原 `add + trimNonStrict + expire` 三次串行；释放判定改用 `lock.isHeldByThread(Thread.currentThread().getId())`，避免远程 `isHeldByCurrentThread()` 的额外 `HEXISTS`；`DistributedLockInterceptor` 用 `ConcurrentHashMap` 缓存已编译的 SpEL `Expression`，不再每次反射解析 AST。
2. `UserAdminServiceImpl` 移除 `redisTemplate.keys("auth:refresh:*")` 全库阻塞扫描；`detail` 改用 `authSessionRepository.listByUser(userId)` 判在线；`setStatus` 禁用用户时在递增 `auth:token_version` 之外追加 `authSessionRepository.deleteAll(userId)`，同步清理 `auth:session:*` 与 `auth:refresh:*`。
3. `AuthServiceImpl.ensureVersion` 去掉 `SETNX` 预置，仅 `GET`（`null` 视为 `0`），鉴权热路径 Redis 请求减半；`RedisAuthSessionRepository.consumeRefresh` 的 `DefaultRedisScript` 提为 `static final` 常量，避免每次重建并重算 SHA1；`RegionServiceImpl.listAllOrdered` 与 `RegionController.tree` 增加 `volatile` 双重检查内存缓存，静态省市区数据只在首次加载/建树一次。仅调整 Redis 访问与缓存，不改变锁语义、围栏令牌单调性与会话失效口径。

---

### P2-6：数据库 `schema.sql` 字段容量缺陷、缺失索引与历史数据归档优化

- **定位**：`backend/env/sql/dm8/schema.sql`

#### 1. 字段定义缺陷
| 表名.字段名 | `schema.sql` 行号 | 当前定义 | 风险与问题 | 建议修改 |
|---|---|---|---|---|
| `t_product.title` | Line 389 | `VARCHAR(20)` | 容量极小（在默认字节语义下仅能存 6 个 UTF-8 汉字），且与 `t_order_info.title VARCHAR(256)`、`t_order_item.product_title VARCHAR(256)` 严重不一致，稍长的商品名称入库即报长度溢出 | 扩大为 `VARCHAR(256) NOT NULL` |
| `t_stock_import.items_json` | Line 967 | `VARCHAR(4000)` | 存放 Excel 批量导入的 JSON 数组（`[{"productId":13,"delta":50},...]`）。一旦单次导入超过约 130 行商品，JSON 长度即超过 4000 字节导致导入或保存直接失败 | 改为 `CLOB NOT NULL` |

#### 2. 缺失的高频查询复合索引与冗余索引
| 表名 | 关联高频查询代码位置 | 现状 | 建议在 `schema.sql` 中新增 / 调整的索引 |
|---|---|---|---|
| `t_order_info` | 用户订单分页 `OrderInfoMapper.xml:15-21` (`WHERE user_id = ? ORDER BY create_time DESC`) | 仅有 `idx_order_lifecycle(user_id, order_status, pay_status, fulfillment_status)`，无法消除按 `create_time` 排序 | `CREATE INDEX idx_order_user_create_time ON t_order_info (user_id, create_time DESC);` |
| `t_order_info` | 管理端待发货分页 `OrderRepositoryImpl.java:116-136` (`WHERE pay_status='PAID' AND fulfillment_status='WAIT_SHIP' AND refund_status='NONE' ORDER BY paid_time ASC, id ASC`) | 无以 `pay_status` 或 `fulfillment_status` 开头的索引，全表扫描 | `CREATE INDEX idx_order_wait_ship ON t_order_info (pay_status, fulfillment_status, refund_status, paid_time, id);` |
| `t_order_info` | 超时关单扫描 `OrderRepositoryImpl.java:139-158` (`WHERE legacy_status='未支付' AND pay_status IN ('UNPAID','PAYING') AND create_time < ? ORDER BY create_time ASC`) | 无以状态+创建时间开头的索引，每 60s 全表扫描 | `CREATE INDEX idx_order_timeout_scan ON t_order_info (order_status, pay_status, create_time);` |
| `t_refund_info` | 退款状态同步扫描 `RefundInfoRepositoryImpl.java:148-165` (`WHERE approval_status='APPROVED' AND refund_status='PROCESSING' ORDER BY create_time ASC`) | 现有两个索引均以 `order_no` 开头，每 60s 全表扫描 | `CREATE INDEX idx_refund_info_sync_scan ON t_refund_info (approval_status, refund_status, create_time);` |
| `t_refund_order` | 重复/晚到支付冲正查重 `RefundOrderRepositoryImpl.java:71-76` (`WHERE payment_no = ? AND apply_type = ?`) | `payment_no` 无索引，全表扫描 | `CREATE INDEX idx_refund_order_payment_apply ON t_refund_order (payment_no, apply_type);` |
| `t_order_shipment` | 模拟物流推进 `OrderShipmentRepositoryImpl.java:53-67` (`WHERE status='SHIPPED' AND shipped_time < ?` / `WHERE status='IN_TRANSIT' AND in_transit_time < ?`) | 仅有 `(status, delivered_time)`，每 15s 全表扫描 | `CREATE INDEX idx_shipment_status_shipped_time ON t_order_shipment (status, shipped_time);`<br>`CREATE INDEX idx_shipment_status_transit_time ON t_order_shipment (status, in_transit_time);` |
| `t_inventory_transaction` | 商品最近流水 `InventoryTransactionRepositoryImpl.java:47-53` (`WHERE product_id = ? ORDER BY create_time DESC, id DESC`) | 仅有单列 `idx_inventory_tx_product(product_id)` | 升级为 `CREATE INDEX idx_inventory_tx_product_time ON t_inventory_transaction (product_id, create_time DESC, id DESC);` |
| `t_cart_item` | `schema.sql:8524-8526` | 已有唯一约束 `uk_cart_user_product(user_id, product_id)`，最左列已是 `user_id` | 删除冗余索引 `idx_cart_item_user(user_id)` |

#### 3. 持续增长的系统表缺少定期清理任务
- `t_order_request` 已配备 `OrderRequestCleanupScheduler`，但 **`t_local_message`**（状态为 `CONSUMED` 且超过 7 天的已消费 Outbox 记录）、**`t_consumer_dedupe`**（超过 7~30 天的去重记录）以及 **`seata_state_machine_inst` / `seata_state_inst`**（已 `SU` 成功的下单 Saga 历史实例）目前无任何过期清理任务，长期运行会持续膨胀。建议参照 `OrderRequestCleanupScheduler` 增加分批（`LIMIT 500`）定期归档/清理调度。

**处理进展（2026-11-24）**：

1. `backend/env/sql/dm8/schema.sql` 已修正字段容量：`t_product.title` 由 `VARCHAR(20)` 扩为 `VARCHAR(256) NOT NULL`（创建服务已校验非空）；`t_stock_import.items_json` 由 `VARCHAR(4000)` 改为 `CLOB NOT NULL`，避免大批量导入 JSON 溢出。
2. 索引已按上表落地：新增 `idx_order_user_create_time`、`idx_order_wait_ship`、`idx_order_timeout_scan`、`idx_refund_info_sync_scan`、`idx_refund_order_payment_apply`、`idx_shipment_status_shipped_time`、`idx_shipment_status_transit_time`；`idx_inventory_tx_product` 升级为 `idx_inventory_tx_product_time(product_id, create_time DESC, id DESC)`；删除与 `uk_cart_user_product` 左前缀重复的 `idx_cart_item_user`。脚本仍为破坏性重建（先 `DROP TABLE ... CASCADE` 再建），可重复执行。
3. 清理任务：新增 `LocalMessageCleanupScheduler`（`mall-infra`，`@ConditionalOnProperty local-message.relay-enabled`），按 `local-message.clean-scan-ms`（默认 10 分钟）分批（每批 500）清理 `CONSUMED` 且超过 7 天的 `t_local_message` 与超过 30 天的 `t_consumer_dedupe`；全局锁 `scheduler:local-message-cleanup` 保证多进程串行，单批失败只记录日志。**Seata 状态机存储暂不引入应用侧重删**：`seata_state_machine_inst` / `seata_state_inst` 由 Seata 引擎维护时序，直接删除存在干扰在途实例的风险，需按官方机制单独评估归档方案，故本轮仅记录为待办。

---

## 5. P3：微服务边界、Feign 治理、依赖与配置优化

### P3-1：`GlobalExceptionHandler` 返回 HTTP 200 导致 `InternalFeignConfiguration.ErrorDecoder` 完全失效与监控失真

- **定位**：
  - `backend/mall-common/src/main/java/cc/ivera/shared/web/GlobalExceptionHandler.java:20-64`
  - `backend/mall-infra/src/main/java/cc/ivera/infra/feign/InternalFeignConfiguration.java:21-51`
  - `backend/mall-infra/pom.xml:33-35, 55`
- **现状与问题**：
  1. `InternalFeignConfiguration` 专门注册了 `ErrorDecoder` 来解析非 2xx 响应中的 `R<?>` 信封并还原为 `BizException`。然而，`GlobalExceptionHandler` 对所有异常（包括 `BizException` 和 500 未知 `Exception`，无差别作用于外部 `/api/**` 和内部 `/internal/**`）一律返回 **HTTP 200 OK**（仅 body 内 `code != 0`）。
  2. 因为 HTTP 状态码恒为 200，OpenFeign **永远不会触发 `ErrorDecoder`**！这带来了两个严重后果：
     - **12+ 个远程适配器重复造轮子**：各服务不得不在 `AbstractPaymentRemoteAdapter`、`ProductServiceRemoteAdapter`、`UserServiceRemoteAdapter`、`CartServiceRemoteAdapter`、`OrderRefundRemoteAdapter`、`PaymentLedgerRemoteAdapter` 等 12 个类中重复手写 `unwrap(R<T>)` 和 `remoteException(code, message)`；
     - **链路追踪与指标失真**：`feign-micrometer` 和 Zipkin 根据 HTTP 状态码判定 RPC 成败，导致内部服务抛出的 `code=500` 系统故障在 Zipkin 与 Micrometer 指标中全部被标记为 **HTTP 200 成功**！
  3. 此外，`mall-infra/pom.xml` 只引入了 `spring-cloud-starter-openfeign`，未引入 `io.github.openfeign:feign-hc5`，导致所有微服务间的 Feign 调用默认退化为 JDK 原生 `HttpURLConnection`（无专用连接池管理）。
- **优化方案**：
  1. 在 `mall-infra` 中提供统一的 Feign `Decoder`（或在 `R<T>` 上提供统一的 `unwrap()` 方法），并在 `GlobalExceptionHandler` 中对 `/internal/**` 请求或通过统一的 Feign 拦截器将 `code >= 500` 的系统异常映射为对应的 HTTP 状态码，让 Zipkin 和 `ErrorDecoder` 正常工作。
  2. 在 `mall-infra/pom.xml` 中引入 `io.github.openfeign:feign-hc5` 开启 Apache HttpClient 5 连接池。

**处理进展（2026-11-24）**：两条均已落地。`GlobalExceptionHandler` 改为返回 `ResponseEntity`，对 `/internal/**` 请求中 `code >= 500` 的错误使用 HTTP 500（body 仍是 `R` 信封，`ErrorDecoder` 可正常解析）；外部 `/api/**` 继续 HTTP 200 + body `code`，公共 API 契约不变。`mall-infra/pom.xml` 引入 `io.github.openfeign:feign-hc5`，内部 Feign 调用启用 Apache HttpClient 5 连接池。各远程适配器原有的 `unwrap(R<T>)` 仍保留，用于解析 HTTP 200 的业务错误（`code=-1` 等），行为不变。

---

### P3-2：API 契约模块（`mall-common`、`mall-api-payment`、`mall-api-refund`、`mall-api-order`）泄漏基础设施依赖

- **定位**：
  - `backend/mall-common/pom.xml:24-28`（强依赖 `mybatis-plus-spring-boot3-starter`）
  - `backend/mall-api-payment/pom.xml:24-36`（作为 API 契约包，竟强依赖 `spring-boot-starter-amqp`、`spring-boot-starter-data-redis`、`wechatpay-apache-httpclient`）
  - `backend/mall-api-payment/src/main/java/cc/ivera/payment/infrastructure/mq/PaymentEventRabbitConfig.java`、`WxPayNotifyHandler.java`、`WechatPay2ValidatorForRequest.java`
  - `backend/mall-api-refund/pom.xml:20-23` 及 `RefundStatusSyncRabbitConfig.java`
  - `backend/mall-api-order/src/main/java/cc/ivera/payment/domain/enums/PayStatus.java`（包名属于 `payment` 却放在 `mall-api-order` 中）
- **影响**：
  - `mall-bill-service` 只是一个纯粹的对账服务（不发 MQ、不收 MQ、不调微信 SDK），仅仅因为引入了 `mall-api-payment` 和 `mall-api-refund` 来读取平台账本，就被迫传递引入了 RabbitMQ Starter、微信支付 SDK，并且被 `@SpringBootApplication(scanBasePackages = "cc.ivera")` 自动扫描注册了 `PaymentEventRabbitConfig`、`RefundStatusSyncRabbitConfig` 和 `WxPayNotifyHandler`！导致 `mall-bill-service` 被迫在 `application.yml` 中配置 RabbitMQ 连接参数才能启动。
  - 同理，`mall-admin-bff` 也因此被迫在启动时连接 DM8、Redis 和 RabbitMQ。
- **优化方案**：
  - `mall-api-*` 模块只保留纯粹的接口、DTO/VO、领域模型、枚举与事件常量（零 Spring `@Configuration` / `@Component` Bean，零 AMQP / Redis / SDK 依赖）；
  - 将 `WxPayNotifyHandler`、`WechatPay2ValidatorForRequest`、`WxPayPrivateKeyUtil` 移回 `mall-payment-service`（`mall-refund-service` 的微信退款通知验签本来就是通过 `PaymentChannelRefundApi.processRefundNotify` 远程委托给 `mall-payment-service` 的，根本不需要在 `mall-api-payment` 中放微信验签类！）；
  - 将 `PaymentEventRabbitConfig` 与 `RefundStatusSyncRabbitConfig` 移至各自需要声明拓扑的服务或标记 `@ConditionalOnProperty`。

**处理进展（2026-11-24）**：已完成。
- `mall-api-payment` 移除 `spring-boot-starter-amqp`、`spring-boot-starter-data-redis`、`wechatpay-apache-httpclient`；`mall-api-refund` 移除 `spring-boot-starter-amqp`。
- `PaymentEventRabbitConfig` 迁至 `mall-infra` 并加 `@ConditionalOnProperty("payment.event.topology-enabled")`，仅在 `mall-payment-service`、`mall-order-service`、`mall-refund-service` 的 `application.yml` 中开启；`RefundStatusSyncRabbitConfig` 迁至 `mall-refund-service`。
- `WxPayNotifyHandler` / `WechatPay2ValidatorForRequest` / `WxPayPrivateKeyUtil` 迁至 `mall-infra`（微信 SDK 声明为 optional + `@ConditionalOnClass`），`mall-payment-service` 与 `mall-refund-service` 显式引入 SDK。退款服务仍需本地验签（`PaymentChannelRefundApi.processRefundNotify` 只做解密，不验签），故未按原方案“只留在支付服务”，改为共享基础设施承载，避免破坏退款通知验签。
- `mall-common` 的 MyBatis-Plus 依赖改为 `<optional>true</optional>`，各服务模块已显式声明，契约模块不再被传递持久层依赖。
- `PayStatus` 从 `mall-api-order` 的 `cc.ivera.payment.domain.enums` 迁至 `mall-common` 的 `cc.ivera.shared.domain.enums`，消除包名与模块归属错位。
- 影响与回滚：新增配置键 `payment.event.topology-enabled`（默认 false）；存量部署未添加该键时支付事件拓扑不再由任意进程声明，需在支付/订单/退款三个服务补齐；回滚只需恢复原 jar 依赖与配置键。

---

### P3-3：清理 `mall-core` 中的 11 个僵尸类与冗余 `RedissonConfig`

- **定位**：
  - `backend/mall-core/src/main/java/cc/ivera/payment/infrastructure/remote/*`（共 9 个类：`AbstractPaymentRemoteAdapter`、`ChannelOrderStatusRemoteAdapter`、`ChannelPaymentQueryRemoteAdapter`、`PaymentChannelRefundRemoteAdapter`、`PaymentCheckoutRemoteAdapter`、`PaymentConfigRemoteAdapter`、`PaymentOrderRemoteAdapter`、`PaymentSuccessRemoteAdapter`、`PaymentServiceClient`）
  - `backend/mall-core/src/main/java/cc/ivera/shared/domain/exception/OversoldException.java`（全仓 0 引用）
  - `backend/mall-core/src/main/java/cc/ivera/shared/domain/model/Money.java`（全仓 0 引用）
  - `backend/mall-core/src/main/java/cc/ivera/shared/infrastructure/config/RedissonConfig.java:10-35`
- **现状与问题**：
  - 只有 `mall-user-bff` 和 `mall-admin-bff` 依赖 `mall-core`。而在支付、订单、退款网关直连拆分完成后，两个 BFF 中没有任何 Controller 调用上述 9 个 Payment 远程适配器（`mall-order-service` 和 `mall-refund-service` 各自维护了自己的 remote adapter）。但这 9 个类留在 `mall-core` 中导致两个 BFF 启动时无端创建了 `PaymentServiceClient` Feign 代理并引入了 `mall-api-payment / order / refund`。
  - `mall-core` 中的 `RedissonConfig` 手工创建 `RedissonClient`，覆盖了 `redisson-spring-boot-starter` 的自动配置，忽略了 `spring.data.redis.timeout`，且无条件调用 `.setPassword(password)`（当 Redis 无密码配置为空串时会发送 `AUTH ""` 报错）。
- **优化方案**：
  - 从 `mall-core` 中删除上述 11 个未使用类及 `RedissonConfig.java`，并从 `mall-core/pom.xml` 中移除 `mall-api-order`、`mall-api-payment`、`mall-api-refund` 依赖；
  - 顺带修正 `CartServiceRemoteAdapter.java:16, 60, 71` 中复制粘贴遗留的错误提示（将 `"用户服务响应为空"` / `"用户服务调用失败"` 修正为 `"购物车服务响应为空"` / `"购物车服务调用失败"`）以及 `OpenApiConfig.java:13` 的旧标题 `"微信支付案例接口文档"`。

**处理进展（2026-11-24）**：已完成。删除 `mall-core` 中 9 个支付远程适配器/客户端、`OversoldException`、`Money` 与 `RedissonConfig`（共 12 个文件），并从 `mall-core/pom.xml` 移除 `mall-api-order`、`mall-api-payment`、`mall-api-refund` 依赖；`RedissonClient` 改由 `redisson-spring-boot-starter` 自动配置。同步修正购物车适配器错误文案与 OpenAPI 标题。全量 `mvn clean test` 通过。

---

### P3-4：DDD 分层依赖违规修复（`application` 层直接依赖 `infrastructure` 实现）

- **定位**：
  - `backend/mall-user-service/src/main/java/cc/ivera/user/application/impl/AuthServiceImpl.java:6-8, 20`：直接 import 了 `cc.ivera.user.infrastructure.security.JwtTokenService`、`LoginGuardService`、`PasswordHasher` 以及 `StringRedisTemplate`。
  - `backend/mall-user-service/src/main/java/cc/ivera/user/application/impl/UserAdminServiceImpl.java:11`：直接注入 `StringRedisTemplate` 拼装 Redis key。
  - `backend/mall-payment-service/src/main/java/cc/ivera/payment/application/impl/wxpay/WxPayOrderService.java:20-23`、`WxPayRefundService.java:17-19`、`WxPayBillService.java:7`：直接依赖 `cc.ivera.payment.infrastructure.gateway.WxPayHttpClient`、`WxPayNotificationDecoder`、`HttpClientUtils`、`WxPayPrivateKeyUtil`。
- **优化方案**：
  遵循 `AGENTS.md` §4 的四层依赖方向（`application -> domain <- infrastructure`），在 `domain.gateway` 或 `domain.service` 中定义抽象端口（如 `TokenPort`、`PasswordHashPort`、`LoginGuardPort`、`WechatPayChannelGateway`），由 `infrastructure` 实现，使 `application` 层只依赖 `domain` 端口。

**处理进展（2026-11-24）**：已完成。`mall-user-service` 新增 `user.domain.gateway` 的 `TokenPort`、`PasswordHashPort`、`LoginGuardPort`、`TokenVersionPort`，`JwtTokenService`、`PasswordHasher`、`LoginGuardService` 改为实现对应端口，新增 `RedisTokenVersionStore` 承接原 `StringRedisTemplate` 版本号读写；`AuthServiceImpl`、`UserAdminServiceImpl` 仅注入端口。`mall-payment-service` 新增 `payment.domain.gateway.WxPayChannelGateway`（含 `WxPayResponse` 记录），由 `WxPayChannelGatewayImpl` 组合 `WxPayHttpClient`、`WxPayNotificationDecoder`、`HttpClientUtils`、`WxPayPrivateKeyUtil`；`WxPayOrderService`、`WxPayRefundService`、`WxPayBillService` 改为只依赖该端口。另外将 `ChannelPaymentQueryDispatcher` 对 `WxChannelPaymentQueryHandler` 实现类的依赖改为对 `ChannelPaymentQueryHandler` 接口的 `@Qualifier` 注入。全量 `mvn clean test` 通过，无 API/配置/DB 变更。

---

### P3-5：Maven 构建工程与模块依赖瘦身

1. **`mall-gateway/pom.xml` 未继承根 `mall-backend` 父 POM**
   - **定位**：`backend/mall-gateway/pom.xml:1-105`
   - **问题**：19 个模块中唯独 `mall-gateway` 未声明 `<parent>`，重复定义了 Spring Boot / Spring Cloud / Spring Cloud Alibaba 版本属性、`<dependencyManagement>` 和编译插件（且漏掉了根 POM 中的 `<parameters>true</parameters>`）。由于根 `backend/pom.xml` 没有直接 `<dependencies>`（只有 `<dependencyManagement>`），继承根 POM 不会污染 WebFlux 依赖。
2. **`mall-payment-service/pom.xml` 残留 18MB 未使用的 Apache POI 依赖**
   - **定位**：`backend/mall-payment-service/pom.xml:90-99`
   - **问题**：对账拆入 `mall-bill-service` 后，`mall-payment-service` 没有任何代码使用 `poi-ooxml` 和 `commons-compress`，可安全移除。
3. **`mall-user-service` 残留未使用的 OpenFeign / LoadBalancer 依赖与 `@EnableFeignClients`**
   - **定位**：`backend/mall-user-service/pom.xml:48-54`、`UserServiceApplication.java:8`
   - **问题**：`mall-user-service` 是最底层领域服务，不调用任何外部 Feign 接口（其 `application.yml:59-60` 中还残留了未使用的 `product.service.base-url` 配置）。
4. **`mall-product-service` 缺失 `logback-spring.xml` 导致 ELK 日志采集中断**
   - **定位**：`backend/mall-product-service/src/main/resources/`、`README.md:616, 2387`
   - **问题**：`README.md` 与 `mall-infra/.../logback-spring.xml` 注释均声称 `mall-product-service` 保留了一份同名 `logback-spring.xml`，但实际上 `mall-product-service/src/main/resources/` 下只有 `application.yml` 和 `mapper/`，**根本没有 `logback-spring.xml`**！导致 `mall-product-service` 的日志完全无法通过 Logstash 采集到 Elasticsearch/Kibana。

**处理进展（2026-11-24）**：已完成 1–3 项。`mall-gateway/pom.xml` 改为继承根 `mall-backend` 父 POM，删除重复的版本属性、`dependencyManagement` 与 `maven-compiler-plugin`；`mall-payment-service/pom.xml` 移除 `poi-ooxml` 与 `commons-compress`；`mall-user-service/pom.xml` 移除 `spring-cloud-starter-openfeign`、`spring-cloud-starter-loadbalancer`，`UserServiceApplication` 删除 `@EnableFeignClients`，`application.yml` 删除未使用的 `spring.cloud.openfeign` 配置块，`mall-infra` 排除项注释改为说明仅用于共享日志与基础设施。第 4 项经核查 `mall-product-service/src/main/resources/logback-spring.xml` 已存在且与 `mall-infra` 内容一致，无需改动。全量 `mvn clean test` 通过，无 API/配置键/DB 变更。

---

### P3-6：跨服务配置一致性、连接池调优与网关韧性治理

1. **`application-core.yml` 漏配 `cart.service.base-url` 导致 `NACOS_ENABLED=false` 直连模式失效**
   - **定位**：`backend/mall-core/src/main/resources/application-core.yml:52-61` vs `CartServiceClient.java:16`
   - **修复**：在 `application-core.yml` 中补齐 `cart.service.base-url: ${CART_SERVICE_BASE_URL:}`，并移除 BFF 不再使用的 `payment.service.base-url` 和 `payment.auth.*`。
2. **`PaymentServiceApplication` 违规开启 `@EnableScheduling`**
   - **定位**：`backend/mall-payment-service/src/main/java/cc/ivera/PaymentServiceApplication.java:9`
   - **修复**：移除 `@EnableScheduling`，严格对齐 `AGENTS.md` §1 规范。
3. **`ProductServiceApplication` 漏加载 `TimeZoneConfig` 与 `ProductionSecretGuard`**
   - **定位**：`backend/mall-product-service/src/main/java/cc/ivera/ProductServiceApplication.java:13-14`
   - **问题**：`scanBasePackages = "cc.ivera.product"` 且 `@Import` 仅引入了 `{MyBatisPlusConfig.class, GlobalExceptionHandler.class}`，漏掉了 `cc.ivera.shared.infrastructure.config.TimeZoneConfig` 和 `ProductionSecretGuard`。
   - **修复**：在 `@Import` 中补齐 `TimeZoneConfig.class` 与 `ProductionSecretGuard.class`；同时在 `ProductionSecretGuard.java:25-33` 中补充对 `stock.import.minio.secret-key`（默认 `hjm029796`）和 `spring.cloud.nacos.password`（默认 `nacos`）的校验，并将 `mall-product-service/src/main/resources/application.yml:40` 的默认 `stock.import.storage` 由硬编码内网 IP（`http://192.168.1.200:39000`）的 `minio` 改为开箱即用的 `local`。
4. **RabbitMQ 消费重试退避参数不一致**
   - **定位**：`mall-order-service/src/main/resources/application.yml:49-54` 与 `mall-refund-service/src/main/resources/application.yml:49-54` 配置为 `initial-interval: 1000, multiplier: 2.0, max-interval: 10000`，而 `README.md:168-170` 和 `application-core.yml:22-25` 为 `2000 / 3.0 / 20000`。应统一对齐。
5. **9 个进程的 HikariCP 连接池与 DM8 `socketTimeout` 调优**
   - **定位**：各服务 `application.yml` 中的 `spring.datasource`
   - **问题**：所有连接 DM8 的进程均未配置 `spring.datasource.hikari.*`，默认每个进程 `minimum-idle: 10, maximum-pool-size: 10`，本地启动全套服务即静默占用 80~90 个 DM8 连接；且 JDBC URL 仅配置了 `connectTimeout=30000`，未配置 `socketTimeout`。
   - **修复**：在 JDBC URL 中补充 `&socketTimeout=60000`，并显式配置 HikariCP `minimum-idle: 2, maximum-pool-size: 10`（低频服务设为 `1 / 5`）；同时为 `mall-cart-service/src/main/resources/application.yml` 补齐 `mybatis-plus.mapper-locations: classpath*:/mapper/**/*.xml`。
6. **网关 `mall-gateway` 补充下游超时、统一 JSON 异常处理与 `X-Forwarded-For` 防伪造**
   - **定位**：`backend/mall-gateway/src/main/resources/application.yml:1-86`、`backend/mall-user-bff/src/main/java/cc/ivera/user/interfaces/AuthController.java:70-76`
   - **修复**：
     - 将 `server.port: 8080` 改为 `${SERVER_PORT:8080}`；
     - 配置 `spring.cloud.gateway.server.webflux.httpclient.connect-timeout: 3000` 与 `response-timeout: 15s`；
     - 新增网关全局 `ErrorWebExceptionHandler`，将 404/503/504 统一包装为 `{ "code": 500, "message": "...", "data": null }` 格式；
     - 对可信代理跳数做限制（或使用 Spring Cloud Gateway `XForwardedRemoteAddressResolver`），防止客户端伪造 `X-Forwarded-For` 欺骗 `AuthSession` 设备 IP 记录。

**处理进展（2026-11-24）**：已完成 1、3、4、5、6 项。`application-core.yml` 补齐 `cart.service.base-url` 并移除 BFF 已不再使用的 `payment.service.base-url` / `payment.auth.*`（两个 BFF 的 Nacos 配置同步清理）；`ProductServiceApplication` 的 `@Import` 补齐 `TimeZoneConfig` 与 `ProductionSecretGuard`，`ProductionSecretGuard` 新增 `stock.import.minio.secret-key`、`spring.cloud.nacos.password` 校验，商品服务 `stock.import.storage` 默认改为 `local`；订单服务与退款服务 RabbitMQ 重试统一为 `2000 / 3.0 / 20000`；7 个领域服务 + 两个 BFF 的 DM8 连接串追加 `socketTimeout=60000` 并配置 HikariCP（常规 `2 / 10`、`mall-bill-service` `1 / 5`），购物车服务补齐 `mapper-locations`；网关端口改为 `${SERVER_PORT:8080}`，新增下游连接 3000ms / 响应 15s 超时、`GatewayErrorWebExceptionHandler` 统一 JSON 错误体与 `TrustedForwardedHeaderFilter` 覆盖客户端 `X-Forwarded-For`（同步更新 `nacos/config/mall-gateway.yaml`）。第 2 项**不实施**：`PaymentServiceApplication` 的 `@EnableScheduling` 是 `mall-payment-service` 中继 `PAYMENT_SUCCEEDED` / `DUPLICATE_PAYMENT_DETECTED` 的 `LocalMessageSendJob` 所必需，且 `AGENTS.md` §1 明确要求支付服务开启 `@EnableScheduling`，移除会中断支付事件 Outbox 投递，原审计结论有误。全量 `mvn clean test` 通过，无 API/DB/MQ 拓扑变更。

---

## 6. P4：前端工程（`user-ui` & `admin-ui`）交互、性能与代码治理

### P4-1：`admin-ui` 首屏加载性能——剥离 `index.html` 中 3.83MB 的同步阻塞 Luckysheet 资源与按需加载 `xlsx`

- **定位**：
  - `admin-ui/index.html:8-13`
  - `admin-ui/src/pages/StockExcelEditor.jsx:4, 53-84`
  - `admin-ui/src/pages/StockMaintenance.jsx:3`、`admin-ui/src/pages/Download.jsx:2`
- **现状与问题**：
  1. `admin-ui/index.html` 的 `<head>` 中同步加载了 `/luckysheet/luckysheet.umd.js`（**3.05 MB**）、`/luckysheet/plugins/js/plugin.js`（**523 KB**）以及 4 个 CSS 文件（**~260 KB**）。这导致管理员访问登录页 `/login` 或订单、商品、对账等任何页面时，浏览器都必须先下载并解析近 **3.83 MB** 的非模块化脚本才能完成首屏渲染，而 Luckysheet 实际上**仅在 `/admin/stock-edit/:id`（`StockExcelEditor.jsx`）一个子页面中使用**！
  2. `StockExcelEditor.jsx:83` 在组件卸载时只设置了 `cancelled = true`，未调用 `window.luckysheet?.destroy?.()`，导致二次进入编辑器时 DOM 与全局事件监听器残留。
  3. `StockMaintenance.jsx`、`StockExcelEditor.jsx`、`Download.jsx` 顶部均使用静态 `import * as XLSX from 'xlsx'`，导致 SheetJS 被急切打包，迫使 `admin-ui/vite.config.js:17` 将 `chunkSizeWarningLimit` 调高至 `1200` KB。
- **优化方案**：
  1. 从 `admin-ui/index.html` 中移除 Luckysheet 的 4 个 `<link>` 和 2 个 `<script>` 标签，封装一个 `loadLuckysheet()` 单例动态脚本加载器，仅在进入 `StockExcelEditor.jsx` 时按需注入，并在 `useEffect` cleanup 中调用 `window.luckysheet?.destroy?.()`。
  2. 将 `import * as XLSX from 'xlsx'` 改为事件触发时的动态导入 `const XLSX = await import('xlsx')`。

**处理进展（2026-11-25）**：已完成。`admin-ui/index.html` 移除 4 个 Luckysheet `<link>` 与 2 个同步 `<script>`（首屏不再加载约 3.83MB）；新增 `admin-ui/src/utils/luckysheet.js` 单例加载器，按需注入 CSS 与 `plugin.js → luckysheet.umd.js`（失败可重试），`StockExcelEditor.jsx` 在 `useEffect` 中 `Promise.all([import('xlsx'), loadLuckysheet()])` 并行加载并在 cleanup 调用 `window.luckysheet?.destroy?.()`；`StockExcelEditor.jsx`、`StockMaintenance.jsx`、`Download.jsx` 的 `xlsx` 改为动作触发时 `await import('xlsx')`，构建产物中 `xlsx` 独立为 429KB 异步 chunk（gzip 143KB），`index.html` 由 3.83MB 同步资源降至 0.76KB。`npm run build` 通过。

---

### P4-2：`user-ui` 核心交易交互缺陷修复（跨页支付轮询、支付宝弹窗拦截、第 101+ 订单退款编辑、结算新建地址）

1. **微信扫码支付轮询只查第 1 页订单，导致第 2+ 页订单付款后二维码弹窗永远不关闭**
   - **定位**：`user-ui/src/pages/OrdersV2.jsx:42-57`
   - **问题**：
     ```javascript
     const timer = setInterval(async () => {
       const o = await checkoutApi.list({ page: 1, size })
       const hit = (o.data?.records || []).find(x => x.order.orderNo === wxCode.orderNo)
       if (hit?.order?.payStatus === 'PAID') { ... }
     }, 3000)
     ```
     每 3 秒拉取第 1 页的 10 笔订单并在数组中查找 `wxCode.orderNo`。如果用户在订单列表翻到了第 2 页并对旧订单发起微信支付，`hit` 永远是 `undefined`，即使支付成功弹窗也永远不会自动感知！此外，当本地未配置公网 ngrok 回调时，仅查本地列表不会触发渠道查单同步。
   - **优化方案**：轮询时直接调用单笔查单接口 `checkoutApi.payQuery(wxCode.orderNo)`（或 `checkoutApi.get(wxCode.orderNo)`），既减少 90% 的响应体积，又不受分页位置影响。

2. **物流状态轮询 `useEffect` 依赖 `[orders]` 导致每次请求后重建定时器且后台标签页持续轮询**
   - **定位**：`user-ui/src/pages/OrdersV2.jsx:36-41`
   - **优化方案**：提取布尔值 `const hasShipped = orders.some(d => d.order.fulfillmentStatus === 'SHIPPED')`，`useEffect` 仅依赖 `[hasShipped, page]`，并在 `document.visibilityState === 'hidden'` 时跳过轮询。

3. **支付宝支付在 `await` 异步请求后调用 `window.open` 被 Safari / 移动端浏览器弹窗拦截**
   - **定位**：`user-ui/src/pages/OrdersV2.jsx:58-62`
   - **问题**：`const r = await checkoutApi.alipay(d.order.orderNo); const w = window.open('', '_blank')` 在 `await` 之后才调用 `window.open`，此时用户点击手势上下文已过期，Safari 及手机浏览器会静默拦截弹窗（`w === null`），用户点击“去支付”后毫无反应。
   - **优化方案**：在 `await` 之前同步打开空白窗口（或在当前页使用隐藏容器提交支付宝 `<form>`），若 `w === null` 则通过 `message.warning` 提示用户允许弹窗或在当前窗口跳转。

4. **`RefundApplications.jsx` 每次翻页附带拉取 100 笔完整订单，且第 101+ 笔订单的退款申请无法编辑保存**
   - **定位**：`user-ui/src/pages/RefundApplications.jsx:19-46, 81`
   - **问题**：`Promise.all([refundApi.mine(nextPage, size), checkoutApi.list({ page: 1, size: 100 })])` 每次进入或翻页都全量拉取前 100 笔订单来构建 `orderItemMap`；一旦用户编辑的是第 101 笔及更早订单的退款申请，`orderItemMap.get(...)` 返回 `undefined`，算出的 `editTotal` 为 `0`，导致保存按钮被 `disabled: editTotal <= 0` 永久禁用！
   - **优化方案**：从 `load()` 中移除 `checkoutApi.list({ page: 1, size: 100 })`；仅在用户点击“修改申请”调用 `beginEdit(row)` 时，按需调用 `checkoutApi.get(row.apply.orderNo)` 获取该笔订单的明细。

5. **`Cart.jsx` 结算页新建收货地址漏传 `provinceCode / cityCode / districtCode`**
   - **定位**：`user-ui/src/pages/Cart.jsx:81-93` vs `user-ui/src/pages/Addresses.jsx:38-48`
   - **问题**：`Cart.jsx` 保存新地址时未传 `provinceCode`、`cityCode`、`districtCode`，导致在购物车页创建的地址后续到 `/addresses` 页面点击编辑时，省市区级联选择器无法回显。
   - **优化方案**：在 `Cart.jsx:84-90` 中补齐 `provinceCode: province || '', cityCode: city || '', districtCode: district || ''`（或抽离共用的 `<AddressFormModal>` 组件）。

**处理进展（2026-11-25）**：已完成 1–5 项。`OrdersV2.jsx` 微信扫码弹窗改为按单号轮询 `checkoutApi.payQuery`（不受分页位置影响，且会触发渠道查单同步）；物流轮询依赖收敛为 `[hasShipped, page]` 且页面隐藏时跳过；支付宝支付在 `await` 前同步 `window.open` 并在被拦截时给出提示；`RefundApplications.jsx` 移除进入/翻页时预拉 100 笔订单，改为 `beginEdit` 时按单号 `checkoutApi.get` 获取明细，第 101+ 笔订单可正常编辑；`Cart.jsx` 新建地址补齐 `provinceCode/cityCode/districtCode`。`npm run test:logic` 与 `npm run build` 通过。

---

### P4-3：`admin-ui` 路由死循环、错误导航、统计口径失真与双重错误弹窗修复

1. **`RequireAdmin` 遇到非管理员 Token 时触发 `/ ↔ /admin/orders` 无限重定向死循环**
   - **定位**：`admin-ui/src/components/RouteGuard.jsx:10-14` 与 `admin-ui/src/App.jsx:30, 46`
   - **问题**：当 `localStorage` 中存在 Token 但 `getUser()?.role !== 'ROLE_ADMIN'` 时，`RequireAdmin` 返回 `<Navigate to="/" replace />`；而 `App.jsx:46` 将 `/` 重定向到 `/admin/orders`（该路由又被 `RequireAdmin` 包裹），形成浏览器无限重定向死循环！
   - **优化方案**：在 `RouteGuard.jsx:13` 中，当 `getUser()?.role !== 'ROLE_ADMIN'` 时先调用 `clearAuth()`，再重定向到 `<Navigate to="/login" replace />`；同时在 `admin-ui/src/pages/Login.jsx:15-20` 中移除非管理员登录时无意义的 `saveAuth(r.data)`。

2. **`AdminLayout.jsx`「前台首页」按钮跳转到管理端自身**
   - **定位**：`admin-ui/src/components/AdminLayout.jsx:47`
   - **问题**：`onClick={() => nav('/')}` 在 `admin-ui`（3002 端口）内跳转到 `#/`，随即被重定向回 `#/admin/orders`，并未打开用户商城（3000 端口）。
   - **优化方案**：改为 `window.open(import.meta.env.VITE_USER_UI_URL || 'http://localhost:3000', '_blank', 'noopener')`。

3. **`Reconciliation.jsx` 顶部 4 个统计卡片仅统计当前页（10 条）而非全量批次**
   - **定位**：`admin-ui/src/pages/Reconciliation.jsx:278-283, 542-563`
   - **问题**：`stats.total = importList.length` 只计算了当前分页返回的最多 10 条记录，但卡片标题写着“导入批次总数”、“已对账批次”、“差异总数”，在多页数据下严重误导管理员。
   - **优化方案**：将 `stats.total` 改为 `importTotal`，并将其余三个指标明确标注为“本页已对账 / 本页失败 / 本页差异数”或由后端返回全局统计汇总。

4. **`admin-ui` API 错误双重弹窗 Toast 与 7 张 `<Table>` 缺失 `loading` 状态**
   - **定位**：
     - `admin-ui/src/utils/request.js:30, 50` 与 `AdminProducts.jsx:39,51,68,73`、`AdminRefunds.jsx:58`、`UserList.jsx:47`、`StockMaintenance.jsx:111,141`
     - `AdminOrders.jsx:197`、`AdminProducts.jsx:110`、`AdminRefunds.jsx:81`、`AdminResetRequests.jsx:52`、`AdminShipping.jsx:33`、`PaymentConfig.jsx:254,275`、`StockMaintenance.jsx:217`
   - **问题**：`request.js` 响应拦截器已经对所有错误弹出了 `message.error`，而各页面组件的 `catch` 块又重复调用了一次 `message.error`，导致每次报错右上角同时弹出两条错误提示；此外上述 7 个页面的 `<Table>` 组件均未绑定 `loading` 状态，还要为 `StockMaintenance.jsx:203` 的“下载模板”按钮补充 `loading` 防抖。

**处理进展（2026-11-25）**：1–4 项已完成。`RouteGuard.RequireAdmin` 对非管理员 Token 先 `clearAuth()` 再跳 `/login`，消除 `/ ↔ /admin/orders` 重定向死循环；`Login.jsx` 移除非管理员登录时的 `saveAuth`；`AdminLayout` 的“前台首页”改为 `window.open(VITE_USER_UI_URL || http://localhost:3000)`；`Reconciliation` 首卡改用 `importTotal`，后三张卡片标注为“本页”口径；移除 `AdminProducts`、`AdminRefunds`、`UserList` 页面 `catch` 中的重复 `message.error`（`StockMaintenance` 仅在无 HTTP 响应时提示本地解析/生成失败），并为 `AdminOrders`、`AdminProducts`、`AdminRefunds`、`AdminResetRequests`、`AdminShipping`、`PaymentConfig`、`StockMaintenance` 的列表补齐 `loading`，为“下载模板”按钮增加 `templateLoading` 防抖。`npm run build` 通过。

---

### P4-4：双端网络层（`request.js`）生产环境 `baseURL` 兜底与并发 401 晚到响应竞态优化

- **定位**：
  - `user-ui/src/utils/request.js:6-8, 35-49`、`user-ui/src/utils/refreshSingleFlight.js:1-9`
  - `admin-ui/src/utils/request.js:6-8, 35-49`、`admin-ui/src/utils/refreshSingleFlight.js:1-9`
- **现状与问题**：
  1. `const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'`：在生产环境构建时若未显式注入 `VITE_API_BASE_URL`（希望走同源 Nginx 反向代理），所有请求都会发往访问者自己电脑的 `http://localhost:8080`。应改为 `import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')`。
  2. **晚到 401 再次触发 Refresh Token 轮换**：`refreshSingleFlight.js` 在 `inFlight.finally(() => { inFlight = null })` 中立即清空 `inFlight`。如果请求 A 和请求 B 都携带旧 AccessToken T1 发出，请求 A 先收到 401 并完成刷新将本地 Token 更新为 T2（此时 `inFlight` 重置为 `null`），紧接着请求 B 的 401 响应才到达拦截器——此时请求 B 会**再次调用 `refreshOnce()`**，导致刚换发的 RefreshToken 被无端二次消耗！
- **优化方案**：
  在 401 拦截器触发 `refreshOnce()` 前，先对比失败请求头中的旧 Token 与当前 `getAccessToken()`：若当前 `getAccessToken()` 已经更新且不等于失败请求携带的 Token，直接用当前新 Token 重放 `service(original)`，无需再次调用 `refreshOnce()`。

**处理进展（2026-11-25）**：已完成。双端 `request.js` 的 `baseURL` 改为 `import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')`；401 拦截器在调用 `refreshOnce()` 前比较失败请求头 Token 与当前 `getAccessToken()`，若已被其它请求刷新则直接以新 Token 重放，避免 Refresh Token 被二次消耗。`user-ui` 的 `npm run test:logic`、`npm run build` 与 `admin-ui` 的 `npm run build` 均通过。

---

### P4-5：大组件拆分、静态资源瘦身与无障碍（a11y）改进

1. **拆分超大单文件组件并脱敏密钥输入框**：
   - 将 `admin-ui/src/pages/Reconciliation.jsx`（819 行，含 28 个 `useState` 和 4 个 Drawer）拆分为主列表与 `ReconcileSummaryDrawer`、`BillRecordsDrawer`、`DiscrepancyListDrawer`、`DiscrepancyDrilldownDrawer` 子组件，避免在备注框每输入一个字符就触发整页重渲染。
   - 在 `admin-ui/src/pages/PaymentConfig.jsx:335-356` 中将 `apiV3Key`、`partnerKey`、`privateKey`、`merchantPrivateKey` 改为 `<Input.Password />` 或带显隐切换的脱敏输入框。
   - 抽取 `admin-ui` 中 `StockMaintenance.jsx` 与 `StockExcelEditor.jsx` 完全重复的 `validateAoa`、`BIZ_TYPES`、`deltaCell` 到公共工具模块。
2. **清理死代码、冗余 CSS 与外部生图 API 硬编码**：
   - `user-ui/src/assets/mallImgs.js:1` 硬编码了外部生图 API `https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?...`，在内网/离线环境下每次加载商品卡片都需等待外网超时后才回退到本地 SVG；建议直接默认使用本地 SVG 插画。
   - 删除 `user-ui/src/assets/css/theme.css`（35 行 100% 未使用）、`global.css`（533 行 98% 未使用）、`reset.css`（含 IE6 `expression(...)` 且覆盖 `antd/dist/reset.css` 焦点轮廓）以及双端 `src/assets/img/` 下未使用的 4 张旧图片；将 `user-ui/src/pages/Success.jsx:18` 的旧文案 `"感谢您的购买，课程已开通"` 更新为商城订单支付成功文案。
   - 移除 `user-ui/src/api/*.js` 中未使用的 28 个管理端 API 方法，移除 `admin-ui/package.json` 中未使用的 `qrcode.react` 依赖，并将双端 `@vitejs/plugin-react` 移入 `devDependencies`。
3. **缓存 `/api/regions/tree` 并补齐 `AppHeader` 搜索与无障碍属性**：
   - 在 `user-ui/src/api/address.js` 中对 `/api/regions/tree` 增加模块级 Promise 记忆化缓存，避免在 `/cart` 与 `/addresses` 间切换时重复请求。
   - `user-ui/src/components/AppHeader.jsx:22-23` 的顶部搜索框目前提交后直接丢弃输入词并跳转 `'/'`，应将搜索关键字通过 URL Query（`/?q=...`）传递给 `Home.jsx` 进行商品过滤，并为 `Cart.jsx` 中的数量加减与删除操作补齐 `aria-label` 与键盘可访问性。

**处理进展（2026-11-25）**：已完成。
- 拆分：`Reconciliation.jsx` 的 4 个 Drawer 与“标记差异已处理”弹窗拆到 `admin-ui/src/components/reconciliation/`（`ReconcileSummaryDrawer`、`BillRecordsDrawer`、`DiscrepancyListDrawer`、`DiscrepancyDrilldownDrawer`、`ResolveDiscrepancyModal`）；备注输入状态收敛到弹窗内部，击键不再重渲染整页。
- 脱敏：`PaymentConfig.jsx` 的 `apiV3Key`、`partnerKey`、`privateKey`、`merchantPrivateKey` 改为 `<Input.Password />`。
- 复用：新增 `admin-ui/src/utils/stockAoa.jsx`，`StockMaintenance.jsx` 与 `StockExcelEditor.jsx` 共用 `BIZ_TYPES`、`STOCK_HEADER`、`deltaCell`、`validateStockAoa`。
- 清理：`mallImgs.js` 改为本地内联 SVG；删除 `theme.css`、`global.css`、`reset.css`（其中 `.loading-container/.loading-text` 保留到 `polish.css`）与 4 张未使用图片；`Success.jsx` 文案改为“订单已支付成功”；删除 `user-ui/src/api/*.js` 中 31 个未使用的管理端方法；移除 `admin-ui` 的 `qrcode.react` 依赖，双端 `@vitejs/plugin-react` 移入 `devDependencies`。
- 缓存与可访问性：省市区树缓存放在新增的 `user-ui/src/api/region.js`（模块级 Promise，失败自动重置），而非审计原文的 `api/address.js`，避免地址 API 模块承担区域树职责；`AppHeader` 搜索框改为 `form` 提交并把关键字以 `?q=` 传给首页（首页按标题过滤，搜索态取前 100 件并隐藏分页）；`Cart.jsx` 勾选、数量加减与删除补齐 `aria-label`，删除操作支持 Enter/Space 键盘触发。
- `user-ui` 的 `npm run test:logic`、`npm run build` 与 `admin-ui` 的 `npm run build` 均通过。

---

## 7. P5：测试覆盖率、ArchUnit 架构守护与 CI/CD 流水线优化

### P5-1：后端核心领域单测补齐（19 个模块中 16 个模块当前为 0 测试）

当前后端仅在 3 个模块中有 4 个测试类（共 13 个 `@Test` 方法）。建议保持“纯域单测 + Mockito 单测（不依赖 DM8 与 Spring 容器，毫秒级执行）”的轻量级风格，按优先级补齐以下高价值单测：

1. **`mall-bill-service`（当前 0 测试，最高优先级）**：
   - 验证 `AGENTS.md` §5.5 账账核对不变量：`1:N PaymentOrder` 按 `channel_order_no` 逐笔匹配、双向单边账（`PAY_CHANNEL_ONLY` / `PAY_LOCAL_ONLY`）、金额/状态不一致、`Asia/Shanghai` `[00:00, 次日00:00)` 日切边界、以及多笔差错金额相抵时 `balanced` 必须为 `false`。
2. **`mall-payment-service`（当前 0 测试）**：
   - 验证 `PaymentSuccessServiceImpl`：同渠道活跃支付单复用、首笔成交写 `t_payment_deal` 并关闭其他活跃尝试并发 `PAYMENT_SUCCEEDED`、不同 `paymentNo` 重复成交冲突触发 `DUPLICATE_PAYMENT_DETECTED`、已关单晚到支付触发 `PAYMENT_LATE_ARRIVED`。
3. **`mall-product-service`（当前 0 测试）**：
   - 验证 `InventoryServiceImpl` 四桶流转（预占 `available -> locked`、确认收货 `locked -> sold`、关单释放 `locked -> available`、仅退款 `locked/sold -> lost`、退货退款 `sold -> available`）及相同 `biz_no` 并发幂等。
4. **`mall-infra`（当前 0 测试）**：
   - 验证 `RedissonDistributedLockTemplate` 的嵌套锁 `FenceTokenHolder` 栈单调不减语义、`nextFenceToken()` 异常时的 `unlock()` 保底释放、以及 `LocalMessageServiceImpl` 的指数退避与状态流转。
5. **`mall-user-service`（当前 0 测试）**：
   - 验证 `AuthServiceImpl` Refresh Token 一次性轮换、旧 Refresh Token 重放时整会话吊销（Replay Detection）及密码修改后全设备下线。

---

### P5-2：将 ArchUnit 架构边界测试扩展至全限界上下文

- **定位**：
  - `backend/mall-refund-service/src/test/java/cc/ivera/refund/architecture/RefundDomainArchitectureTest.java`
  - `backend/mall-order-service/pom.xml:108`、`mall-payment-service/pom.xml:116`、`mall-bill-service/pom.xml:101`、`mall-core/pom.xml:83`
- **现状与问题**：
  `mall-order-service`、`mall-payment-service`、`mall-bill-service`、`mall-core` 的 `pom.xml` 中已经引入了 `com.tngtech.archunit:archunit` 依赖，但**没有编写任何 ArchUnit 测试类**；而 `mall-product-service`、`mall-user-service`、`mall-cart-service` 则既未引入依赖也无测试。此外现有的 `RefundDomainArchitectureTest` 仅校验了 `domain` 层，未校验 `AGENTS.md` §4 强制要求的“`application` 不得反向依赖 `interfaces`”与“PO 只属于 `infrastructure.persistence.po`”。
- **优化方案**：
  在 `order / payment / bill / product / user / cart / refund` 七个领域服务中统一落地 `*DomainArchitectureTest`，同时断言：
  1. `..domain..` 不依赖 Spring、MyBatis-Plus、`..infrastructure..`、`..application..`、`..interfaces..`；
  2. `..application..` 不依赖 `..interfaces..` 与 `..infrastructure.persistence.po..`。

---

### P5-3：前端逻辑测试扩展与静态检查（ESLint）接入

- **定位**：`user-ui/tests/refresh-single-flight.test.mjs`、`user-ui/src/utils/refundQuota.js`、`admin-ui/package.json`
- **优化方案**：
  1. 在 `user-ui/tests/` 下利用 Node 原生 `node --test` 补充 `refund-quota.test.mjs`（验证前端可退数量计算与最后一笔尾差金额估算逻辑与后端 `RefundPolicy` 一致）和 `auth-store.test.mjs`。
  2. 在 `admin-ui/package.json` 中新增 `"test:logic": "node --test tests/*.test.mjs"`，覆盖 `refreshSingleFlight.js` 及 Excel 导入校验函数 `validateAoa`、`aoaToLuckysheet`。

---

### P5-4：GitHub Actions CI（`.github/workflows/ci.yml`）构建提速与并发治理

- **定位**：`.github/workflows/ci.yml:1-52`
- **优化方案**：
  1. **增加 `concurrency` 自动取消旧流水线**：同一分支快速连续推送时自动取消正在运行的旧 Workflow，节省 Runner 资源。
  2. **Maven 并行安静构建**：将 `mvn -B clean test` 升级为 `mvn -B -ntp -T 1C clean test`（启用 `--no-transfer-progress` 减少日志噪音，并按 CPU 核心数并行构建无依赖关系的模块）。
  3. **设置 `timeout-minutes` 防止任务挂死**：为 `backend` 设置 `timeout-minutes: 15`，为 `frontend` 设置 `timeout-minutes: 10`。
  4. **开启 `admin-ui` 逻辑测试**：在 `admin-ui` 补齐 `test:logic` 后，移除第 49 行的 `if: matrix.app == 'user-ui'` 限制。

**处理进展（2026-11-25）**：P5-2/3/4 已完成。
- P5-2：给 `mall-product-service`、`mall-user-service`、`mall-cart-service` 补齐 archunit 依赖（`archunit:1.5.1` test scope，与已有 `mall-refund-service`、`mall-order-service`、`mall-payment-service`、`mall-bill-service` 统一），7 个领域服务全部落地 `*DomainArchitectureTest`，每条测试覆盖 "domain 不依赖 Spring/MyBatis-Plus/外层四层" 与 "application 不依赖 interfaces/infrastructure.persistence.po" 两条规则；原有 `RefundDomainArchitectureTest` 也同步扩展为两条规则；全量 14 条 ArchUnit 测试通过。`mall-infra` 为跨域共享基础设施库而非限界上下文，不适用四层依赖规则，未加 ArchUnit。
- P5-3：`admin-ui/package.json` 新增 `test:logic`（`node --test "tests/*.test.mjs"`），`user-ui` 的 `test:logic` 从单文件改为 glob 模式；新增 `admin-ui/tests/stock-aoa.test.mjs`（`validateStockAoa` 跳过表头/空行、解析数字字符串 ID、收集非法行）；新增 `user-ui/tests/refund-quota.test.mjs`（可退数量/金额预估/尾差规则/边界，口径与后端 `RefundPolicy` 一致）；同时把 `admin-ui/src/utils/stockAoa.jsx` 拆为纯 JS 核心 `stockAoaCore.js`（Node 测试可直接 import）和 React 入口 `stockAoa.jsx`（在核心基础上加 JSX `deltaCell`）。双端逻辑测试共 9 条全部通过，`admin-ui` 构建不受影响。
- P5-4：`.github/workflows/ci.yml` 新增 `concurrency` 自动取消旧流水线，`mvn -B clean test` → `mvn -B -ntp -T 1C clean test`，`backend timeout-minutes: 15`、`frontend timeout-minutes: 10`，移除 `if: matrix.app == 'user-ui'` 让 `admin-ui` 也执行 `test:logic`。
- P5-1（领域单测补齐）已落地：后端测试基线从 30 条 → **72 条全绿**，新增 42 条：
  - `mall-infra`（11 条新增）：`FenceTokenHolderTest`（5 条 ThreadLocal 栈语义：嵌套单调不减、多线程隔离、栈空幂等 release、heldMillis 非负）、`RedissonDistributedLockTemplateTest`（6 条：参数校验 lockKey/supplier 空、加锁失败抛 BizException、`nextFenceToken` 异常时 finally 仍解锁防 Watchdog 永久泄漏、supplier 异常时 finally 解锁、成功路径审计记录）
  - `mall-product-service`（13 条新增）：`InventoryServiceImplTest` Mockito mock 3 个 Repository，覆盖排序防死锁、幂等跳过（DuplicateKeyException 不调 reserveStock）、库存不足事务失败、LOCKED→COMMITTED 幂等已提交、已 RELEASED 预占不可提交、已 COMMITTED 预占不可释放、`convertToSoldOnReceipt` 的 restockedQty 扣减、restock/核销的 `afterReceipt` 桶分流（未收货从 locked 桶 / 已收货从 sold 桶）
  - `mall-payment-service`（4 条新增）：`PaymentSuccessServiceImplTest` Mockito mock 6 个依赖，覆盖渠道交易号已绑定幂等、正常成交关其它渠道活跃 + 发 PAYMENT_SUCCEEDED、同 paymentNo 幂等返回 false、不同 paymentNo 触发 DUPLICATE_PAYMENT_DETECTED 冲正
  - `mall-user-service`（6 条新增）：`AuthServiceImplRefreshTest` Mockito mock 6 个依赖，覆盖 Replay Detection（consumeRefresh 返回 null + 找到已用 sid 则 delete 吊销）、版本失效（改密码 bumpVersion 后旧 refresh token 抛异常）、正常轮换三步（rotate + saveRefresh + markRefreshUsed）、空参数、格式错误
  - `mall-bill-service`（9 条新增）：抽 `BillBalancedRule` 纯静态类到 `domain/policy`（29 行 JDK 零依赖），内联 balanced 计算 6 条件 && 逻辑从 BillReconcileServiceImpl 搬入；9 条测试覆盖 6 条件全一致→true、任何条件不一致→false、**净额差为 0 但 open 差异存在→false**（AGENTS.md 严禁仅凭净额判平账）、日切无交易全 0→true

---

## 8. 建议落地路线图（Roadmap）

为遵循 [AGENTS.md](AGENTS.md) **“小步、可验证、可回滚、与现有领域规则一致”** 的原则，建议按以下 4 个阶段分批建分支推进：

1. **阶段一：安全与核心正确性止血（`bug-fix/*`，预计 1~2 天）**
   - 修复 **P0-1**（`AuthInterceptor` 白名单与管理接口权限校验）、**P0-2**（`OrderInfoController` 越权修复）、**P0-3**（`RedissonDistributedLockTemplate` 异常锁释放保底）、**P0-4**（`InventoryServiceImpl` 先写流水防 TOCTOU 双扣）、**P0-5**（`ExceptionRefundServiceImpl` 冲正重试短路修复）、**P0-6**（`TimeoutOrderCloseScheduler` 纳入 `PAYING` 状态）、**P0-7**（`OrderNoUtils` 线程安全改造）、**P0-8**（`HttpClientUtils` 恢复标准 SSL 校验与连接池超时）。
2. **阶段二：锁围栏透传、领域不变量对齐与前端交易 Bug 修复（`bug-fix/*`，预计 2~3 天）**
   - 落地 **P1-1 ~ P1-8**（Feign 透传 `X-Fence-Token`、收敛同聚合根锁 Key、补齐未发货取消 `addRestockedQty` 守卫与关单前 `hasSuccessfulPayment` 检查、消除 Outbox 初次提交竞态与 `STATUS_SENT` 覆盖、补发 V2 `REFUND_SYNC` 消息、移除无 `LIMIT` 异常回退查询）。
   - 修复 **P4-2 & P4-3**（`user-ui` 跨页扫码轮询、支付宝弹窗、退款申请按需查单、购物车新建地址编码；`admin-ui` 非管理员登录重定向死循环与统计口径）。
3. **阶段三：数据库索引、长事务拆解、批量化与前端首屏性能（`refactor/*` / `feature/*`，预计 3~4 天）**
   - 在 `schema.sql` 中落地 **P2-6**（扩充 `t_product.title` 与 `t_stock_import.items_json`，补齐 7 个复合索引）；
   - 落地 **P2-1 ~ P2-5**（剥离事务内远程调用、消除 `RefundOrderServiceImpl.details` 等 N+1 查询、`WxTradeBillParser` 改用 POI SAX 流式解析与批量落库、移除 Redis `KEYS` 阻塞命令、合并分布式锁 Redis RTT）；
   - 落地 **P4-1**（`admin-ui` 按需动态加载 Luckysheet 与 SheetJS，首屏减少 3.83MB 阻塞资源）。
4. **阶段四：微服务模块依赖治理、测试矩阵与 CI 升级（`refactor/*` / `update/*`，预计 2~3 天）**
   - 落地 **P3-1 ~ P3-6**（统一 Feign 错误解码与 `feign-hc5` 连接池、剥离 `mall-api-*` 中的基础设施实现、清理 `mall-core` 僵尸类、补齐 `mall-product-service` 的 `logback-spring.xml` 与 `TimeZoneConfig/ProductionSecretGuard`）；
   - 落地 **P5-1 ~ P5-4**（补齐 5 个核心模块的领域单测与全模块 ArchUnit 守护，升级 `.github/workflows/ci.yml`），并同步更新 `README.md` 第 15 章。
