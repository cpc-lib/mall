# 任务清单（tasks.md）

> 对应 [spec.md](spec.md)。按顺序执行，每步都给出可验证的完成判据。

## T1 收敛退款侧分布式锁 Key（P1-2）

- [x] 改 `RefundApplicationServiceImpl` 4 个 `@DistributedLock`：
  - `approve` / `reject` / `queryRefundStatus` → `'refund:order:' + #refundNo`
  - `reconcileOrderRefundStatus` → `'refund:create:' + #orderNo`
- [x] 改 `RefundInfoServiceImpl.createRefundApplication` → `'refund:create:' + #orderNo`
- [x] 改 `OrderRefundStatusService.refreshOrderRefundStatus` → `"refund:create:" + orderNo`
- 验证：`Grep "payment:refund:|refund:status:" backend/mall-refund-service` 无命中。

## T2 订单状态推进加锁（P1-3.4）

- [x] 在 `OrderInfoServiceImpl` 增加 `ORDER_STATUS_LOCK_WAIT_MS = 3000L`
- [x] `updateStatusByOrderNoIfStatus` 改为在 `order:status:{orderNo}` 锁内调用新私有方法 `casLegacyStatusAdvance`
- [x] 原逻辑（CAS、库存提交/释放、关闭活跃支付单、`markConsumed`、日志）原样搬迁，不改行为
- 验证：`AdminOrderShipmentController.forceClose`、`markPaid`、`OrderInternalApiAdapter.closeIfNotPay` 均走到该方法；无远程调用进入锁内。

## T3 文档同步

- [x] `OPTIMIZE.md` 为 P1-1、P1-2、P1-3、P1-4、P1-7、P1-8 各补 `**处理进展（2026-11-25）**`
- [x] `Grep "payment:refund:|refund:status:" README.md`，命中则同步锁 Key 描述，未命中不改
- 验证：文档与代码锁键、实现状态一致。

## T4 回归与交付

- [x] 设置 `JAVA_HOME=D:\develop\java\jdk21.0.11_10` 后执行 `mvn -o -B -ntp -T 1C clean test`
- [x] `git status` 确认只含预期文件（4 个 Java + OPTIMIZE.md，必要时 README.md）
- [x] 提交 `fix: 收敛退款锁键并补齐订单状态推进锁` 并推送 `origin/main`
- 验证：构建全绿、工作区干净（无关脏文件保持 stash，不提交）。
