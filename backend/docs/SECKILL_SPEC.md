# 秒杀功能实施规格（SECKILL_SPEC）

| 项目 | 内容 |
|---|---|
| 文档状态 | 实施规格，待评审后进入开发 |
| 上游文档 | [SECKILL_DESIGN.md](SECKILL_DESIGN.md)（架构与方案） |
| 适用模块 | `mall-seckill-service`（新增）、`mall-order-service`、`mall-product-service`、`mall-gateway`、`user-ui`、`admin-ui` |
| 数据权威 | `backend/env/sql/dm8/schema.sql` |

本规格把设计文档细化为可开发、可验收的条目。若与设计文档表述不一致，以本规格为准并回写设计文档。

## 1. 术语

| 术语 | 含义 |
|---|---|
| 活动 Activity | 秒杀营销活动的壳，包含多个场次 |
| 场次 Session | 一段可抢购时间窗，是预热、开始、结束的最小单位 |
| 秒杀 SKU | 场次内的一个商品及其秒杀价、限购、配额 |
| 配额 Quota | 秒杀可售名额账本，独立于商品四桶库存 |
| 请求 Request | 一次抢购动作，唯一 `requestId`，一请求最多一单 |
| 预扣 | Redis Lua 中的原子扣减，不代表下单成功 |
| 锁配额 | 落单时数据库配额 `available → locked` |

## 2. 需求条目

### 2.1 功能需求

| 编号 | 需求 | 优先级 | 验收 |
|---|---|---|---|
| FR-01 | 管理员可创建、编辑、删除未发布活动 | P0 | AC-01 |
| FR-02 | 管理员可在活动下维护场次与秒杀 SKU | P0 | AC-01 |
| FR-03 | 发布前校验：配额不超过商品可用库存；同商品场次时间不重叠 | P0 | AC-02 |
| FR-04 | 场次按时间窗自动 ONLINE / ENDED，ONLINE 前自动预热 | P0 | AC-03 |
| FR-05 | 用户可查看进行中/即将开始的活动、场次、SKU 与倒计时 | P0 | AC-04 |
| FR-06 | 用户在时间窗内可提交抢购，接口只返回 `requestId`，不同步建单 | P0 | AC-05 |
| FR-07 | Redis 预扣必须原子完成「配额判断 + 限购判断 + 扣减」 | P0 | AC-06、AC-07 |
| FR-08 | 同一用户同一 `Idempotency-Key` 重复提交返回同一 `requestId` | P0 | AC-08 |
| FR-09 | 预扣成功后异步建单，订单 `order_type=SECKILL`，价格为秒杀价 | P0 | AC-09 |
| FR-10 | 用户可按 `requestId` 轮询抢购结果 | P0 | AC-10 |
| FR-11 | 超时未支付关单/取消必须同时释放商品库存与秒杀配额 | P0 | AC-11 |
| FR-12 | 支付成功后配额 `locked → sold`，商品库存语义不变 | P0 | AC-12 |
| FR-13 | 退款只回补商品库存，不回补秒杀配额 | P0 | AC-13 |
| FR-14 | 消费失败重试耗尽进入 Failure Queue；超时 PENDING 由调度器补偿 | P0 | AC-14、AC-15 |
| FR-15 | Redis 丢失/服务重启后可按 DB 重建配额与用户已抢数量 | P1 | AC-16 |
| FR-16 | 提供用户级频率限制与全局开关 `seckill.enabled` | P1 | AC-17 |
| FR-17 | 管理端可查看配额、订单、支付、失败数量与结余报表 | P1 | AC-18 |
| FR-18 | 提供 Redis 与 DB 配额对账任务，只告警不自动改写 | P1 | AC-19 |

### 2.2 非功能需求

| 编号 | 指标 | 目标 |
|---|---|---|
| NFR-01 | 超卖 | 0 |
| NFR-02 | 抢购接口延迟 | P99 ≤ 200ms（服务端，不含公网） |
| NFR-03 | 抢购到出单延迟 | P99 ≤ 3s |
| NFR-04 | 重复请求 | 同一幂等键只产生一个请求、最多一个订单 |
| NFR-05 | 可用性 | `seckill.enabled=false` 时不影响普通交易链路 |
| NFR-06 | 可观测 | 抢购、落单、补偿、对账均有日志与指标 |
| NFR-07 | 编译门禁 | `mvn clean test` 通过；前端构建通过 |

## 3. 角色与权限

| 角色 | 能力 |
|---|---|
| 游客 | 可查看活动列表/详情，不可抢购 |
| 登录用户 | 抢购、轮询自己的请求、支付与售后 |
| 管理员 | 活动/场次/SKU 管理、预热、上下线、监控 |

- 抢购与结果查询必须登录；结果查询只能访问本人 `requestId`。
- 管理端接口沿用现有管理员鉴权；普通用户调用返回 HTTP 403。
- `/internal/seckill/**` 不经过网关，仅供服务间调用。

## 4. 业务规则

| 编号 | 规则 |
|---|---|
| BR-01 | 一个请求只含一个 SKU；`quantity` 默认 1，且不得超过限购 |
| BR-02 | 场次 ONLINE 后禁止修改秒杀价、配额、限购；只允许下线 |
| BR-03 | 同一商品在重叠时间窗内只允许一个进行中场次 |
| BR-04 | 配额满足 `available_quota + locked_quota + sold_quota = total_quota` |
| BR-05 | Redis 未预热或 key 缺失一律按售罄拒绝（fail-close），不得放行到 DB |
| BR-06 | Redis 预扣成功不等于下单成功；最终结果以数据库订单为准 |
| BR-07 | 落单顺序：商品库存预占 → 配额锁定 → 写订单与 Outbox；任一步失败整体回滚 |
| BR-08 | 一请求一单：订单侧以 `requestId` 作为下单幂等键 |
| BR-09 | 关单/取消释放配额；支付成功消耗配额；退款不回补配额 |
| BR-10 | 所有配额变更写流水，`biz_no` 唯一，重复执行返回原结果 |
| BR-11 | 禁止使用 `select ... for update`；状态推进使用条件 UPDATE + 唯一键 |
| BR-12 | 补偿任务只补偿不反超：不得用 Redis 数值覆盖 DB |

## 5. 状态机

### 5.1 活动

```text
DRAFT → PUBLISHED → ONLINE → OFFLINE
                    ONLINE → ENDED
PUBLISHED → DRAFT（允许撤回，前提：无 ONLINE 场次）
```

- 删除仅允许 `DRAFT`，采用逻辑删除。
- `ENDED` 为终态；`OFFLINE` 可人工重新 `ONLINE`，前提是当前时间仍在活动起止内。

### 5.2 场次

```text
DRAFT → ONLINE → ENDED
ONLINE → OFFLINE → ONLINE
任意非终态 → WARMING → ONLINE（预热窗口）
```

- `WARMING`：预热/重建中，抢购入口关闭，返回「活动准备中」。
- 到达 `start_time` 且预热完成才允许 `ONLINE`；到达 `end_time` 自动 `ENDED`。
- `ENDED` 后 Redis key 保留至 `end_time + 1 天` 过期。

### 5.3 抢购请求

```text
PENDING → ORDERED（订单已创建，回写 order_no）
PENDING → FAILED（建单失败且补偿耗尽，释放配额）
PENDING → CANCELLED（用户取消或场次下线，释放配额）
ORDERED 为终态；后续支付/履约状态以订单为准
```

对外轮询映射：`PENDING → PROCESSING`，`ORDERED → SUCCESS`，`FAILED/CANCELLED → FAILED`。

### 5.4 配额

```text
available → locked（SECKILL_LOCK:requestId）
locked → sold（SECKILL_COMMIT:orderNo）
locked → available（SECKILL_RELEASE:orderNo）
```

## 6. 数据字典

通用字段：`id BIGINT IDENTITY(1,1)` 主键；`create_time/update_time TIMESTAMP`；金额单位为分；时间均为数据库时间戳。

### 6.1 `t_seckill_activity`

| 字段 | 类型 | 必填/默认 | 约束与说明 |
|---|---|---|---|
| `activity_no` | VARCHAR(50) | 必填 | 唯一，规则 `SA` + 日期 + 序列 |
| `title` | VARCHAR(256) | 必填 | 活动标题 |
| `start_time/end_time` | TIMESTAMP | 必填 | `end_time > start_time` |
| `status` | VARCHAR(16) | `DRAFT` | 见 5.1 |
| `version` | INT | 0 | 乐观锁 |
| `fence_token` | BIGINT | 0 | 围栏令牌，状态 UPDATE 携带 |

### 6.2 `t_seckill_session`

| 字段 | 类型 | 必填/默认 | 约束与说明 |
|---|---|---|---|
| `activity_id` | BIGINT | 必填 | 所属活动 |
| `session_no` | VARCHAR(32) | 必填 | 与 `activity_id` 联合唯一 |
| `start_time/end_time` | TIMESTAMP | 必填 | 必须落在活动时间窗内 |
| `status` | VARCHAR(16) | `DRAFT` | `DRAFT/WARMING/ONLINE/OFFLINE/ENDED` |
| `version/fence_token` | - | 0 | 同活动表 |

索引：`(status, start_time)` 用于状态扫描。

### 6.3 `t_seckill_sku`

| 字段 | 类型 | 必填/默认 | 约束与说明 |
|---|---|---|---|
| `session_id` | BIGINT | 必填 | 所属场次 |
| `product_id` | BIGINT | 必填 | 与 `session_id` 联合唯一 |
| `seckill_price` | INT | 必填 | 秒杀价（分），必须小于商品原价 |
| `per_user_limit` | INT | 1 | 每人每场限购 |
| `total_quota` | INT | 必填 | 配额总量 |
| `available_quota` | INT | = `total_quota` | 剩余配额 |
| `locked_quota` | INT | 0 | 已锁未支付 |
| `sold_quota` | INT | 0 | 已支付消耗 |
| `status` | VARCHAR(16) | `ENABLED` | `ENABLED/DISABLED` |
| `version/fence_token` | - | 0 | 同上 |

### 6.4 `t_seckill_request`

| 字段 | 类型 | 必填/默认 | 约束与说明 |
|---|---|---|---|
| `request_id` | VARCHAR(64) | 必填 | 唯一，UUID 去横线 |
| `idem_key` | VARCHAR(128) | 必填 | 与 `user_id` 联合唯一 |
| `user_id` | BIGINT | 必填 | 抢购用户 |
| `activity_id/session_id/sku_id/product_id` | BIGINT | 必填 | 快照关联 |
| `quantity` | INT | 必填 | 购买数量 |
| `seckill_price` | INT | 必填 | 请求时秒杀价快照 |
| `status` | VARCHAR(16) | `PENDING` | 见 5.3 |
| `order_no` | VARCHAR(50) | 可空 | 建单成功后回写 |
| `reject_reason` | VARCHAR(200) | 可空 | 失败原因 |
| `request_hash` | VARCHAR(64) | 必填 | 请求体 SHA-256，用于幂等回放校验 |
| `retry_count` | INT | 0 | 补偿重投次数 |
| `expire_at` | TIMESTAMP | 必填 | 请求结果保留期 |
| `version/fence_token` | - | 0 | 同上 |

索引：`(session_id, sku_id, user_id, status)` 用于限购重建；`(status, update_time)` 用于补偿扫描。

### 6.5 `t_seckill_quota_transaction`

| 字段 | 类型 | 说明 |
|---|---|---|
| `biz_no` | VARCHAR(128) | 唯一，`SECKILL_LOCK:requestId` 等 |
| `biz_type` | VARCHAR(32) | `SECKILL_LOCK/SECKILL_COMMIT/SECKILL_RELEASE` |
| `session_id/sku_id` | BIGINT | 配额归属 |
| `request_id/order_no` | VARCHAR | 关联单号 |
| `available_delta/locked_delta/sold_delta` | INT | 三态变化量 |

### 6.6 订单扩展

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `t_order_info.order_type` | VARCHAR(16) | `NORMAL` | `NORMAL/SECKILL`；存量数据无需迁移 |

## 7. API 契约

通用约定：

- 响应统一 `R` 信封：`{ "code": 0, "message": "成功", "data": ... }`。
- 业务失败 `code = -1`，`message` 为固定文案，HTTP 200；参数错误 `code = 400`；未登录 HTTP 401；无权限 HTTP 403。
- 抢购接口必须携带请求头 `Idempotency-Key`（长度 8–128）。
- 所有时间字段输出 `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai`。

### 7.1 活动列表

`GET /api/seckill/activities`

响应 `data`：

```json
[
  {
    "activityId": 1001,
    "title": "双十一秒杀",
    "sessions": [
      {
        "sessionId": 2001,
        "status": "ONLINE",
        "startTime": "2026-11-14 20:00:00",
        "endTime": "2026-11-14 21:00:00",
        "serverTime": "2026-11-14 19:59:30",
        "skus": [
          {
            "skuId": 3001,
            "productId": 88,
            "title": "商品标题快照",
            "seckillPrice": 9900,
            "originalPrice": 19900,
            "totalQuota": 100,
            "availableQuota": 42,
            "perUserLimit": 1,
            "soldOut": false
          }
        ]
      }
    ]
  }
]
```

- `serverTime` 用于前端倒计时校准时钟偏移。
- 列表只返回 `ONLINE` 与未来 24 小时内开始的场次；不暴露任何用户维度数据。

### 7.2 提交抢购

`POST /api/seckill/orders`

请求：

```json
{ "sessionId": 2001, "skuId": 3001, "quantity": 1 }
```

成功响应：

```json
{
  "code": 0,
  "message": "成功",
  "data": { "requestId": "7d9c2f0a1b4e4f3c9a2d", "status": "PROCESSING" }
}
```

失败文案（`code = -1`）：

| 场景 | message |
|---|---|
| 未开始/已结束/下线 | `活动未开始或已结束` |
| 预热中 | `活动准备中，请稍后再试` |
| 配额不足 | `已售罄` |
| 超限购 | `超出限购数量` |
| 频率超限 | `操作过于频繁，请稍后再试` |
| 全局关闭 | `活动未开放` |

### 7.3 结果轮询

`GET /api/seckill/requests/{requestId}`

```json
{
  "code": 0,
  "data": {
    "requestId": "7d9c2f0a1b4e4f3c9a2d",
    "status": "SUCCESS",
    "orderNo": "SK202611142000010001",
    "quantity": 1,
    "seckillPrice": 9900,
    "reason": null
  }
}
```

- 非本人请求返回 `code = 404`。
- 前端轮询间隔建议 1s，超过 30s 仍为 `PROCESSING` 时提示「结果确认中」，不得重复提交新请求。

### 7.4 管理端

| 接口 | 说明 |
|---|---|
| `POST/PUT /api/admin/seckill/activities` | 新建/编辑活动 |
| `DELETE /api/admin/seckill/activities/{id}` | 逻辑删除，仅 DRAFT |
| `POST /api/admin/seckill/activities/{id}/publish` | 发布并做 BR-02/BR-03 校验 |
| `POST /api/admin/seckill/activities/{id}/offline` | 下线活动及其进行中场次 |
| `POST/PUT /api/admin/seckill/sessions` | 维护场次 |
| `POST/PUT /api/admin/seckill/skus` | 维护 SKU 与配额 |
| `POST /api/admin/seckill/sessions/{id}/warmup` | 手动预热/重建 |
| `GET /api/admin/seckill/sessions/{id}/metrics` | 配额、订单、支付、失败统计 |

### 7.5 内部接口

| 接口 | 请求要点 | 幂等 |
|---|---|---|
| `GET /internal/seckill/skus/{skuId}` | 返回价格、限购、状态、场次时间窗 | 只读 |
| `POST /internal/seckill/quota/lock` | `{requestId, skuId, quantity}` | `SECKILL_LOCK:requestId` |
| `POST /internal/seckill/quota/commit` | `{orderNo, requestId}` | `SECKILL_COMMIT:orderNo` |
| `POST /internal/seckill/quota/release` | `{orderNo, requestId, reason}` | `SECKILL_RELEASE:orderNo` |
| `GET /internal/seckill/requests/{requestId}` | 返回状态与 `orderNo` | 只读 |

- 重复 `lock` 返回成功且配额不变；重复 `commit/release` 同样幂等返回。
- `lock` 时配额不足返回业务失败，订单服务必须回滚并释放商品预占。

## 8. MQ 契约

### 8.1 拓扑

```text
seckill.event.exchange
  ├─ seckill.order.requested → seckill.order.create.queue    （order-service）
  └─ seckill.order.created   → seckill.order.created.queue   （seckill-service）

seckill.event.failure.exchange
  ├─ seckill.order.create.failure.queue
  └─ seckill.event.parking-lot.queue
```

### 8.2 `SECKILL_ORDER_REQUESTED`

```json
{
  "eventId": "7d9c2f0a1b4e4f3c9a2d",
  "requestId": "7d9c2f0a1b4e4f3c9a2d",
  "userId": 10001,
  "activityId": 1001,
  "sessionId": 2001,
  "skuId": 3001,
  "productId": 88,
  "quantity": 1,
  "seckillPrice": 9900,
  "requestTime": "2026-11-14 20:00:01"
}
```

- Outbox：`t_local_message.biz_type = SECKILL_ORDER_REQUESTED`，`biz_no = requestId`。
- 消费去重：`t_consumer_dedupe('order.seckill-requested', requestId)`。
- 订单侧幂等：`t_order_request.idem_key = requestId`，`api = MQ:/seckill/order`。

### 8.3 `SECKILL_ORDER_CREATED`

```json
{
  "eventId": "SK202611142000010001",
  "requestId": "7d9c2f0a1b4e4f3c9a2d",
  "orderNo": "SK202611142000010001",
  "userId": 10001,
  "skuId": 3001,
  "quantity": 1,
  "amount": 9900,
  "createTime": "2026-11-14 20:00:02"
}
```

- 消费去重：`t_consumer_dedupe('seckill.order-created', orderNo)`。
- 消费动作：请求置 `ORDERED` 并回写 `order_no`；重复消息直接 ACK。

### 8.4 可靠性

- MANUAL ACK：业务完成 → 去重回写 → `basicAck`；异常向外抛，不提前 ACK/NACK。
- 重试耗尽进入 Failure Queue；Parking Lot 不自动回灌。
- 中继只由 `mall-user-bff` 执行；`mall-seckill-service` 设 `local-message.relay-enabled=false`。

## 9. Redis 契约

| Key | 类型 | 说明 |
|---|---|---|
| `seckill:stock:{skuId}` | String | 剩余配额；缺失按售罄处理 |
| `seckill:user:{skuId}` | Hash | field = userId，value = 已抢数量 |
| `seckill:idem:{userId}:{idemKey}` | String | value = requestId，`NX EX 600` |
| `seckill:session:{sessionId}` | String | 场次缓存，60s |
| `seckill:rate:{userId}` | String | 频率计数，1s |

Lua 返回值：

| 返回值 | 含义 | 处理 |
|---|---|---|
| `>= 0` | 预扣成功，值为剩余配额 | 写请求与 Outbox |
| `-1` | 配额不足或未预热 | 返回「已售罄」 |
| `-2` | 超限购 | 返回「超出限购数量」 |

释放动作必须与 `SECKILL_RELEASE:*` 流水绑定：先成功写流水，再 `INCRBY` 库存、`HINCRBY -qty` 用户计数。

## 10. 并发与一致性

- DB 是配额与库存的最终权威；Redis 仅做闸门，任何情况下不得以 Redis 覆盖 DB。
- 配额 UPDATE 必须带数量条件，例如 `available_quota >= qty`；商品库存继续走 `reserveStock`。
- 落单事务内的远程调用失败必须回滚本地事务，并执行对应补偿。
- 限购由 Redis 保证峰值性能，由 `(session_id, sku_id, user_id)` 请求记录与配额流水兜底审计。
- 同一用户并发点击、网络重试、MQ 重复投递，最终都只能产生一个有效订单。

## 11. 异常与补偿矩阵

| 场景 | 检测 | 补偿 | 幂等锚 |
|---|---|---|---|
| 预扣成功但请求落库失败 | 接口异常 | 对账/重建修正 Redis | `requestId` |
| Outbox 投递失败 | `t_local_message` 重试超限 | 告警 + 人工/重投 | `biz_no` |
| 建单消费失败 | Failure Queue / PENDING 超时 | 查单、重投、释放 | `requestId` |
| 建单成功但回写失败 | PENDING 超时 | 查单回写 ORDERED | `orderNo` |
| 商品预占成功但配额锁失败 | 落单异常 | 回滚并释放商品预占与 Redis | `orderNo` |
| 关单释放部分成功 | 对账差异 | 重试释放 | `SECKILL_RELEASE:orderNo` |
| Redis 数据丢失 | 健康检查/对账 | 按 DB 重建，入口置 WARMING | - |

补偿任务：`SeckillRequestRecoveryScheduler`、`SeckillSessionScheduler`、`SeckillQuotaReconcileScheduler`。
单条失败只记录日志，不中断批次；对账只告警，不自动改写。

## 12. 验收用例

| 编号 | 用例 | 步骤摘要 | 预期 |
|---|---|---|---|
| AC-01 | 活动维护 | 创建活动/场次/SKU 并编辑 | 保存成功，DRAFT 可删 |
| AC-02 | 发布校验 | 配额大于商品可用库存；配置时间重叠场次 | 均拒绝并提示原因 |
| AC-03 | 场次推进 | 到达开始/结束时间 | 自动 ONLINE/ENDED，开始前完成预热 |
| AC-04 | 活动展示 | 用户打开秒杀页 | 倒计时基于 serverTime，售罄置灰 |
| AC-05 | 正常抢购 | 提交抢购并轮询 | 返回 requestId，最终 SUCCESS 且可支付 |
| AC-06 | 超卖 | 并发请求数大于配额 | 成功订单数不超过配额，超卖为 0 |
| AC-07 | 限购 | 同一用户重复购买超过限购 | 返回「超出限购数量」 |
| AC-08 | 幂等 | 同一幂等键并发提交 10 次 | 仅一条 request、最多一单，返回同一 requestId |
| AC-09 | 订单快照 | 查看秒杀订单 | `order_type=SECKILL`，明细单价为秒杀价 |
| AC-10 | 结果查询 | 查询他人 requestId | 返回 404，不泄露信息 |
| AC-11 | 超时关单 | 抢购后不支付至关单 | 商品库存与配额均释放，流水各一条 |
| AC-12 | 支付成功 | 完成支付 | 配额 locked→sold，商品库存不变 |
| AC-13 | 退款 | 秒杀订单退款成功 | 商品库存按规则回补，配额 sold 不变 |
| AC-14 | 消费失败 | 注入建单异常至重试耗尽 | 进入 Failure Queue，无脏订单 |
| AC-15 | 补偿 | 停掉消费者制造 PENDING 超时后恢复 | 查单补偿或释放，不重复建单 |
| AC-16 | 重建 | 删除 Redis key 后触发重建 | 入口先 WARMING，重建后配额与 DB 一致 |
| AC-17 | 限流与开关 | 超过频率阈值；关闭 `seckill.enabled` | 分别返回限流文案与「活动未开放」 |
| AC-18 | 运营统计 | 查看场次指标 | 配额、订单、支付、失败数与明细一致 |
| AC-19 | 对账 | 人工制造 Redis/DB 差值 | 产生告警，不自动改库 |
| AC-20 | 回归 | 普通商品下单、支付、退款 | 行为与改造前一致 |

## 13. 上线检查清单

- [ ] `schema.sql` 已合入新表与 `order_type` 列，并在全新库、存量库各执行一次。
- [ ] 网关新增路由顺序正确：`/api/admin/seckill/**`、`/api/seckill/**` 位于更宽泛规则之前。
- [ ] Nacos 新增 `mall-seckill-service.yaml`，服务注册与配置导入正常。
- [ ] RabbitMQ 新拓扑声明成功；未修改既有队列参数。
- [ ] `mall-seckill-service` 的 `local-message.relay-enabled=false`。
- [ ] 管理端完成发布校验、预热、下线验证。
- [ ] AC-05～AC-16 全部通过；AC-20 普通交易回归通过。
- [ ] `mvn clean test`、`user-ui` 构建、`admin-ui` 构建通过。
- [ ] `README.md`（含第 15 章代码导览）、`AGENTS.md`、`RABBITMQ_OPERATIONS.md` 已同步。
- [ ] 回滚开关 `seckill.enabled=false` 验证有效。

## 14. 需求追踪

| 需求 | 设计章节 | 验收 |
|---|---|---|
| FR-01～FR-04 | 设计 5、11、12 | AC-01～AC-03 |
| FR-05～FR-10 | 设计 6、7、12 | AC-04～AC-10 |
| FR-11～FR-13 | 设计 9、10 | AC-11～AC-13 |
| FR-14～FR-15 | 设计 8、11 | AC-14～AC-16 |
| FR-16～FR-18 | 设计 13、15 | AC-17～AC-19 |
