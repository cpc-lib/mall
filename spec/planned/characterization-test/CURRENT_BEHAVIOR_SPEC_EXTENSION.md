# 当前行为 SPEC — 特征测试扩展

> 适用项目：Payment Demo V5
> 状态：planned（特征测试待生成）
> 领域（feature-domain）：characterization-test
> 更新于：2026-06-14
> 影响范围：backend | database | redis | rabbitmq | external-provider | tests
> 前置：[PAYMENT_DEMO_CURRENT_BEHAVIOR_SPEC.md](../implemented/current-behavior/PAYMENT_DEMO_CURRENT_BEHAVIOR_SPEC.md)

本 spec 是对已有 `PAYMENT_DEMO_CURRENT_BEHAVIOR_SPEC.md` 的扩展，基于架构梳理后新增的行为面。不判断当前行为是否合理，不提出重构方案。

## 1. 订单创建/复用行为

### 1.1 订单创建参数校验

| 场景 | 当前行为 |
|---|---|
| `productId=null` | 抛 `BizException("商品ID不能为空")` |
| `paymentType=""` | 抛 `BizException("支付方式不能为空")` |

### 1.2 订单复用规则

| 场景 | 当前行为 |
|---|---|
| 同一 `productId + paymentType` 存在 NOTPAY 订单 | 复用已有订单，不新建 |
| 同一 `productId + paymentType + paymentAppId` 存在 NOTPAY 订单 | 复用已有订单 |
| 不同 `paymentAppId` | 分别创建独立订单，不互相复用 |
| `paymentAppId=null` 时的复用 | 与 `paymentAppId=null` 的 NOTPAY 订单复用 |

### 1.3 订单创建并发控制

| 层级 | 当前行为 |
|---|---|
| 分布式锁 key | `payment:order:create:{productId}:{paymentType}:{paymentAppId 或 "default"}` |
| 锁参数 | waitTime=3000ms, leaseTime=10000ms |
| 锁内操作 | `SELECT ... FOR UPDATE` + 查询未支付订单 → 不存在则 INSERT |
| DuplicateKeyException 兜底 | 重新查询未支付订单，若仍无则重新抛异常 |

### 1.4 延迟关单消息

| 场景 | 当前行为 |
|---|---|
| 新订单创建后 | 在事务提交后发送延迟消息（`TransactionSynchronizationManager.afterCommit`） |
| 无事务上下文 | 直接发送消息，不等待 afterCommit |
| 消息内容 | `OrderCloseMessage{orderNo, paymentType}` |
| RabbitMQ 路由 | `payment.order.close.event.exchange` → `payment.order.close.delay` routing key |

### 1.5 saveCodeUrl 幂等

| 场景 | 当前行为 |
|---|---|
| `orderNo` 或 `codeUrl` 为空 | 直接返回，不操作 |
| 订单 `code_url` 已有值 | UPDATE 条件包含 `code_url IS NULL OR code_url=''`，不覆盖，`updated=0` |
| 订单 `code_url` 为空 | 正常写入 |

## 2. 支付通知处理行为

### 2.1 支付宝通知

| 场景 | 当前行为 |
|---|---|
| 验签 | 用订单绑定的支付应用的 `alipayPublicKey` 验签 |
| 金额校验 | `total_amount`（元）转分后与 `order.totalFee` 比较，不一致返回 `failure` |
| seller_id 校验 | 与支付应用的 `sellerId` 比较，不一致返回 `failure` |
| app_id 校验 | 与支付应用的 `alipayAppId` 比较，不一致返回 `failure` |
| trade_status 非 TRADE_SUCCESS | 返回 `failure` |
| 通知处理锁 key | `payment:ali:notify:pay:{orderNo}`，waitTime=5000ms, leaseTime=30000ms |
| 状态更新 CAS | 仅当 `order_status=未支付` 时才更新为 `支付成功` |
| `total_amount` 为空时金额校验 | `validateAliPayOrderNotify` 中跳过金额校验（warn 日志） |

### 2.2 微信 V3 通知

| 场景 | 当前行为 |
|---|---|
| 通知解密 | `WxPayNotificationDecoder.decryptResource()` 使用订单绑定支付应用配置 |
| 通知处理锁 key | `payment:wx:notify:pay:{orderNo}`，waitTime=5000ms, leaseTime=-1（看门狗） |
| 金额校验 | 通知中的 `amount.payer_total` 与 `order.totalFee` 比较 |
| 状态更新 CAS | 仅当 `order_status=未支付` 时才更新为 `支付成功` |

### 2.3 微信 V2 通知

| 场景 | 当前行为 |
|---|---|
| 验签 | `WXPayUtil.isSignatureValid(body, wxPayConfig.getPartnerKey())` |
| 幂等机制 | Redis SETNX `payment:wx:v2:notify:processed:{transactionId}`，TTL=24h |
| 幂等值 | 处理前设为 `"processing"`，成功标记为 `"processed"` |
| 验签失败 | `releaseNotifyLock`（仅当值仍为 `"processing"` 时删除） |
| 业务失败 | `releaseNotifyLock`（仅当值仍为 `"processing"` 时删除） |
| XML 解析失败 | 直接返回 `<xml><return_code><![CDATA[FAIL]]></return_code>...>`，不触发 Redis/锁/DB |
| 锁 key | `payment:wx:v2:notify:pay:{orderNo}`，waitTime=5000ms, leaseTime=-1 |

## 3. 支付流水记录行为

### 3.1 微信 V3 流水

| 场景 | 当前行为 |
|---|---|
| `payment_type` | 固定 `WXPAY` |
| `trade_type` | 取通知报文中的 `trade_type` 字段 |
| `trade_state` | 取通知报文中的 `trade_state` 字段 |
| `payer_total` | 取 `amount.payer_total`，类型转换为 Integer |
| 重复流水 | `DuplicateKeyException` 被 catch，打日志，不抛异常 |

### 3.2 微信 V2 流水

| 场景 | 当前行为 |
|---|---|
| `payment_type` | 固定 `WXPAY` |
| `trade_type` | 取通知报文中的 `trade_type` 字段 |
| `trade_state` | 取通知报文中的 `result_code` 字段（注意不是 `trade_state`） |
| `payer_total` | 取通知报文中的 `total_fee` 字段，`Integer.valueOf()` |
| 重复流水 | 同 V3 |

### 3.3 支付宝流水

| 场景 | 当前行为 |
|---|---|
| `payment_type` | 固定 `ALIPAY` |
| `trade_type` | 固定 `"电脑网站支付"`（硬编码） |
| `trade_state` | 取通知报文中的 `trade_status` 字段 |
| `payer_total` | `total_amount`（元）转分，`MoneyUtils.yuanToCents()` |
| `content` | 通知参数 JSON 序列化 |
| 重复流水 | 同 V3 |

## 4. 退款行为

### 4.1 退款申请

| 场景 | 当前行为 |
|---|---|
| 订单状态可退款 | `SUCCESS`、`PARTIAL_REFUND`、`REFUND_PROCESSING` 可申请 |
| 订单状态不可退款 | 其他状态抛 `BizException("当前订单状态不允许申请退款：" + status)` |
| 可退余额计算 | `订单总金额 - 已申请退款金额总和（含 CREATED/PROCESSING/FAILED 等所有非 SUCCESS 状态）` |
| 可退余额为 0 | 抛 `BizException("金额已经全部退还处理")` |
| `refundAmount=null` | 使用剩余可退金额作为实际退款金额 |
| `refundAmount` 超过可退余额 | 抛 `BizException("退款申请金额超过可退余额")` |
| 退款单号生成 | `OrderNoUtils.getRefundNo()` |
| 初始状态 | `approval_status=PENDING`, `refund_status=CREATED` |
| 重复退款单号 | `DuplicateKeyException` 被 catch 后抛 `BizException("退款申请单重复提交")` |

### 4.2 退款审核

| 场景 | 当前行为 |
|---|---|
| 审核通过 | 分布式锁 `payment:refund:approve:{refundNo}`，waitTime=5000ms |
| 已拒绝的审核通过 | 抛 `BizException("退款申请单已拒绝，不能审核通过")` |
| 已退款成功重复审核 | 抛 `BizException("该退款申请单已退款成功，请勿重复处理")` |
| 已在处理中重复审核 | 抛 `BizException("该退款申请单已在退款处理中，请勿重复处理")` |
| 审核通过后的状态流转 | `markApprovalPassed()` → `updateRefund → PROCESSING` → 执行退款 → 发延迟同步消息 |
| 退款执行失败 | `markRefundSubmitFailed()` → 状态改为 FAILED → 重新抛异常 |
| 审核拒绝 | 分布式锁 `payment:refund:reject:{refundNo}`，`markApprovalRejected()` |
| 审核事务传播 | `approve()` 使用 `Propagation.NOT_SUPPORTED`（不使用外部事务） |

### 4.3 退款状态刷新

| 场景 | 当前行为 |
|---|---|
| 全额退款成功 | `successRefundAmount >= totalFee` → 订单状态改为 `REFUND_SUCCESS` |
| 部分退款成功 | `successRefundAmount > 0` → 订单状态改为 `PARTIAL_REFUND` |
| 无成功但有处理中 | `processingRefundAmount > 0` → 订单状态改为 `REFUND_PROCESSING` |
| 无成功/处理中但有异常 | `abnormalRefundAmount > 0` → 订单状态改为 `REFUND_ABNORMAL` |
| 无任何退款 | → 订单状态恢复为 `SUCCESS` |
| 状态优先级 | 全额成功 > 部分成功 > 处理中 > 异常 > 恢复成功 |

## 5. 配置加载行为

| 场景 | 当前行为 |
|---|---|
| 启动时数据库不可用 | catch `DataAccessException`，清空缓存，warn 日志，应用继续启动 |
| `reloadConfigs()` | 清空缓存 → 重新加载渠道 → 重新加载应用 |
| 渠道缓存 key | `channelCode`（String） |
| 应用缓存 key | `appId`（Long） |
| 应用加载条件 | 仅加载 `ENABLED` 状态的渠道和应用 |
| 应用校验 | 应用的 `channelCode` 必须在渠道缓存中存在才加载 |
| JSON 解析失败 | 该应用跳过，不加载到缓存 |
| 管理端 CRUD 后 | 自动调用 `reloadConfigs()` |
| 手动 reload | `POST /api/payment-config/reload` |

## 6. RabbitMQ 消息路由

### 6.1 订单关闭延迟消息

| 属性 | 当前值 |
|---|---|
| Exchange | `payment.order.close.event.exchange` |
| Delay Queue | `payment.order.close.delay.queue` |
| Dead Letter Exchange | `payment.order.close.dead-letter.exchange` |
| Release Queue | `payment.order.close.release.queue` |
| Delay Routing Key | `payment.order.close.delay` |
| Release Routing Key | `payment.order.close.release` |
| 默认 TTL | 60000ms（可配置 `payment.order.close-delay-ms`） |

### 6.2 退款状态同步延迟消息

| 属性 | 当前值 |
|---|---|
| Exchange | `payment.refund.status-sync.event.exchange` |
| Delay Queue | `payment.refund.status-sync.delay.queue` |
| Dead Letter Exchange | `payment.refund.status-sync.dead-letter.exchange` |
| Release Queue | `payment.refund.status-sync.release.queue` |
| Delay Routing Key | `payment.refund.status-sync.delay` |
| Release Routing Key | `payment.refund.status-sync.release` |
| 默认 TTL | 60000ms（可配置 `payment.refund.status-sync-delay-ms`） |

## 7. 可疑行为汇总（必须原样锁住）

| # | 可疑行为 | 位置 | 锁住要求 |
|---|---------|------|---------|
| 1 | `queryOrderStatus` 非 SUCCESS 一律返回 code=101+"支付中" | `OrderInfoController.queryOrderStatus()` | 测试必须断言所有非 SUCCESS 状态返回相同结构 |
| 2 | 旧退款入口 `refundAmount=null` → 全额退款 | `RefundApplicationController.applyLegacy()` | 测试必须验证 null 传递 |
| 3 | 支付宝流水 `trade_type` 硬编码 `"电脑网站支付"` | `PaymentInfoServiceImpl.createPaymentInfoForAliPay()` | 测试必须断言硬编码值 |
| 4 | 微信 V2 流水 `trade_state` 取 `result_code` 而非 `trade_state` | `PaymentInfoServiceImpl.createPaymentInfoForWxPayV2()` | 测试必须断言此映射 |
| 5 | 支付流水重复插入被吞掉 | `PaymentInfoServiceImpl.insertPaymentInfoIdempotently()` | 测试必须验证不抛异常 |
| 6 | 微信 V2 通知 XML 解析失败直接返回 FAIL | `WxPayV2Controller.wxNotify()` | 测试必须验证不触发下游 |
| 7 | 启动时 DB 不可用不阻断应用 | `PaymentConfigLoader.init()` | 测试必须验证 warn 日志 + 空缓存 |
| 8 | 关单不是无条件关闭，而是先查渠道 | `OrderCloseConsumer.handleOrderClose()` | 测试必须验证查渠道逻辑 |
| 9 | `saveCodeUrl` 不覆盖已有值 | `OrderInfoServiceImpl.saveCodeUrl()` | 测试必须验证不覆盖 |
| 10 | 退款状态优先级：全额>部分>处理中>异常>恢复 | `OrderRefundStatusService.refreshOrderRefundStatus()` | 测试必须覆盖每个优先级分支 |
| 11 | `payment-config/apps` 返回运行时对象引用 | `PaymentConfigController.apps()` | 测试必须验证 isSameAs |
