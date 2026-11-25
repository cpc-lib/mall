# P1/P2 优化项收尾规格（spec.md）

> 范围：`OPTIMIZE.md` 第 3 章 P1-1～P1-8 与第 4 章 P2-1～P2-6。
> 结论：P2 六项均已有「处理进展」并已落地，本轮只做回归验证，不改代码。
> P1 大部分已在下线（P0/P6/P7 轮次）实现但缺文档记录；仅 P1-2、P1-3 存在真实缺口。

## 1. 现状核实（已读代码证据）

| 项 | 状态 | 证据 |
|---|---|---|
| P1-1 Feign 围栏令牌透传 | 已实现，缺文档 | `mall-remote` 的 `InternalFeignConfiguration` 注入 `X-Fence-Token`；`mall-infra` 的 `InternalFenceTokenFilter` 回填 `FenceTokenHolder` |
| P1-2 锁 Key 统一 | **部分缺口** | 支付侧已统一为 `payment:order:{orderNo}`；退款 V2 已统一 `refund:order:{refundNo}` / `refund:create:{orderNo}`；但 V1 兼容链路仍为 `payment:refund:approve|reject|query|reconcile:*`、`payment:refund:create:*`、`refund:status:{orderNo}` |
| P1-3 锁/CAS 补齐 | **一处缺口** | ①`RefundOrderServiceImpl.queryRefundStatus` 已改条件 UPDATE；②`RefundQuotaReleasedListener` 已 `markFailedIfStatusIn`；③支付宝 `cancelOrder`/`checkOrderStatus` 已加 `payment:order:` 锁；⑤`PasswordResetRequestServiceImpl` 已有 `@Transactional` + `markResolvedIfStatusIn`；④订单侧消费者/强制关单仍无锁 |
| P1-4 领域不变量 | 已实现 | 未发货取消走 `restockWithGuard`；`forceClose`、微信/支付宝 `cancelOrder` 均先查 `paymentDealRepository.existsByOrderNo`；消费者终态仍调 `ensureReservationCommitted/Released`；退款链路已「先冻结渠道资金、后回补库存」 |
| P1-5 Saga 顺序 | 已实现（有记录） | `ReserveInventory → CreateOrder → ClearCart` |
| P1-6 Outbox | 已实现（有记录） | 按域中继 + 应用名隔离锁键 |
| P1-7 REFUND_SYNC | 已实现，缺文档 | `RefundOrderServiceImpl` 与 `ExceptionRefundServiceImpl` 的 `executeChannelRefund` 在 `PROCESSING` 时均发延迟同步消息 |
| P1-8 定时任务 | 已实现，缺文档 | 两个仓储已删除 `catch (RuntimeException)` 无 LIMIT 回退；7 个 `@Scheduled` 均用 `tryExecute` 非阻塞锁 |
| P2-1～P2-6 | 已实现（有记录） | 见 `OPTIMIZE.md` 494/537/557/572/612/645 行处理进展 |

## 2. 目标变更

### 2.1 P1-2：退款侧锁 Key 收敛（仅改字符串，不改逻辑）

统一规则：
- 退款单级（`refundNo`）→ `refund:order:{refundNo}`
- 订单级退款创建/对账（`orderNo`）→ `refund:create:{orderNo}`

具体改动：

1. `backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundApplicationServiceImpl.java`
   - `approve` 注解：`'payment:refund:approve:' + #refundNo` → `'refund:order:' + #refundNo`
   - `reject` 注解：`'payment:refund:reject:' + #refundNo` → `'refund:order:' + #refundNo`
   - `queryRefundStatus` 注解：`'payment:refund:query:' + #refundNo` → `'refund:order:' + #refundNo`
   - `reconcileOrderRefundStatus` 注解：`'payment:refund:reconcile:' + #orderNo` → `'refund:create:' + #orderNo`
2. `backend/mall-refund-service/src/main/java/cc/ivera/refund/application/impl/RefundInfoServiceImpl.java`
   - `createRefundApplication` 注解：`'payment:refund:create:' + #orderNo` → `'refund:create:' + #orderNo`
3. `backend/mall-refund-service/src/main/java/cc/ivera/refund/application/OrderRefundStatusService.java`
   - `refreshOrderRefundStatus` 锁键：`"refund:status:" + orderNo` → `"refund:create:" + orderNo`

死锁评估（已核）：持有 `refund:create:{orderNo}` 的路径（create/cancelPaidOrder/trigger/对账）内部不获取 `refund:order:{refundNo}`；`refund:order:{refundNo}` 路径（settle/签收/重试）内部不再获取 `refund:create`。Redisson 锁同线程可重入，V1 对账在持锁内调用 `refreshOrderRefundStatus` 不会自锁。

### 2.2 P1-3.4：订单状态推进统一加锁（`order:status:{orderNo}`）

`backend/mall-order-service/src/main/java/cc/ivera/order/application/impl/OrderInfoServiceImpl.java`

- 新增常量 `ORDER_STATUS_LOCK_WAIT_MS = 3000L`。
- `updateStatusByOrderNoIfStatus` 保留参数空值校验后，改为：
  `distributedLockTemplate.execute("order:status:" + orderNo, ORDER_STATUS_LOCK_WAIT_MS, () -> casLegacyStatusAdvance(orderNo, currentStatus, targetStatus))`
- 把原方法第 190～211 行的日志、`casLegacyStatus`、库存提交/释放、关闭活跃支付单、`markConsumed` 逻辑原样搬入私有方法 `casLegacyStatusAdvance`。
- 覆盖路径：`PaymentSucceededConsumer` / `OrderCloseConsumer` / `TimeoutOrderCloseScheduler` → `markPaid` → 本方法；`AdminOrderShipmentController.forceClose` 与支付侧 `closeIfNotPay`（`OrderInternalApiAdapter`）→ 本方法。
- 锁内仅执行本地 DB 写与 afterCommit 注册，无远程调用；`markPaid` 为同线程自调用，不重复取锁（锁在方法内部，非注解 AOP）。

## 3. 文档

1. `OPTIMIZE.md`：为 P1-1、P1-2、P1-3、P1-4、P1-7、P1-8 各补一段 `**处理进展（2026-11-25）**`，写明「已实现项 / 本轮收敛项 / 验证方式」。P1-5、P1-6、P2-1～P2-6 已有记录，不动。
2. `README.md`：先 grep `payment:refund:`、`refund:status:`、`payment:order:`、`order:status:`；若第 15 章列出具体锁 Key 则同步，未列出则不改。

## 4. 影响面与回滚

- 影响：仅分布式锁 Key 字符串与订单状态推进的加锁位置；状态机、库存四桶、退款防超退、MQ ACK 语义、HTTP 契约、DB schema 均不变。
- 回滚：回退本提交即可；新旧锁 Key 无持久化状态，滚动发布期间同一聚合短暂使用两套 Key 只会退化为旧的弱互斥，不会产生错误数据。
- 兼容性：无 API/DB/MQ 契约变化，无需数据迁移。

## 5. 验证（成功标准）

1. `mvn -o -B -ntp -T 1C clean test` 全绿（需先设置 JDK21 的 `JAVA_HOME`/`PATH`）。
2. `grep` 全仓确认不再出现 `payment:refund:approve|reject|query|reconcile|create` 与 `refund:status:` 锁键。
3. `git status` 仅包含本规格涉及的 4 个 Java 文件 + `OPTIMIZE.md`（+ 可能的 `README.md`）。
4. 提交信息示例：`fix: 收敛退款锁键并补齐订单状态推进锁`（不含 Agent/模型署名），推 `origin/main`。

## 6. 明确不做

- 不新增单测（改动为锁键字符串与加锁位置，无新业务分支；以全量编译+既有测试回归为准）。
- 不改 `WxPayOrderService` 中按 `productId` 建单的锁（下单时尚无 `orderNo`，属不同聚合）。
- 不动 P2 代码、不清理 Seata 历史表、不做无关格式化。
