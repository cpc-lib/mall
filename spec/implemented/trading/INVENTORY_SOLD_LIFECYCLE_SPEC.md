# INVENTORY_SOLD_LIFECYCLE_SPEC — 库存三桶模型（可用/锁定/已售）与确认收货结转

- 状态：`planned`
- 日期：2026-09-06
- 问题分类：Design change（领域模型 / 状态规则 / DB schema / 流水契约变更）
- 需求来源：用户反馈"用户确认收货之后，锁定的库存没有被解锁，也没有转到已出售"，要求核对逻辑并修改，考虑幂等。

## 1. 问题与目标

### 1.1 现状（V2，缺陷）

| 环节 | 动作 | 问题 |
|---|---|---|
| 下单预占 | available-=qty, locked+=qty | 正确 |
| 支付成功提交 | `commitReservedStock`：locked-=qty | 库存从所有桶消失（不可解释），且与"确认收货才结转已售"的业务预期不符 |
| 确认收货 | 无任何库存动作 | 锁定库存既不解锁也不结转已售 |
| 退款回补 | available+=qty（不扣来源桶） | 与三桶模型账实不符 |

### 1.2 目标（三桶模型）

```
t_product：available_stock（可用） / locked_stock（锁定：已预占未结转） / sold_stock（已售：确认收货结转，新增）

下单预占        ：available -= qty, locked += qty          （不变）
支付成功提交    ：库存数量不变（保持锁定，仅 reservation LOCKED→COMMITTED）  （变更）
确认收货        ：locked -= qty, sold += qty               （新增结转）
未支付关单/取消 ：locked -= qty, available += qty          （不变）
退款回补-未收货 ：locked -= qty, available += qty          （变更：扣来源桶）
退款回补-已收货 ：sold   -= qty, available += qty          （变更：扣来源桶）
管理员手工调整  ：available ± delta                        （不变）
```

- 确认收货时点：`ShipmentService.confirmReceipt`（物流单 CAS DELIVERED→RECEIVED 之后，同事务）。
- 结转数量口径：按订单明细 `qty = t_order_item.quantity - t_order_item.restocked_qty`（收货前已退款回补的部分不重复结转），qty<=0 跳过。
- 退款回补分流依据：退款类型硬校验已保证 CANCEL_BEFORE_SHIP 仅 WAIT_SHIP（未收货）、RETURN_AND_REFUND 仅 RECEIVED（已收货），调用方显式传 `afterReceipt`。

## 2. 数据库变更

### 2.1 全量脚本 payment_demo.sql（新装库）

- t_product：新增 `sold_stock INT DEFAULT 0 NOT NULL`（列序在 locked_stock 之后）+ 列注释。
- t_inventory_transaction：新增 `sold_delta INT NOT NULL DEFAULT 0`？否——按现有列风格 `sold_delta INT NOT NULL`（与 available_delta/locked_delta 一致）+ 列注释；biz_type 注释补 ORDER_SOLD。

### 2.2 增量脚本 upgrade_inventory_sold_stock.sql（存量库）

1. `ALTER TABLE t_product ADD sold_stock INT DEFAULT 0 NOT NULL;`
2. `ALTER TABLE t_inventory_transaction ADD sold_delta INT DEFAULT 0 NOT NULL;`
3. 列注释（COMMENT ON COLUMN）。
4. 存量数据回填（一次性账实校正）：
   - sold_stock = 已收货（fulfillment_status='RECEIVED'）订单明细 `SUM(quantity - NVL(restocked_qty,0))`；
   - locked_stock = 未支付预占（reservation status='LOCKED' SUM(quantity)）+ 在途已支付（pay_status='PAID' 且 fulfillment_status IN ('WAIT_SHIP','SHIPPED')）明细 `SUM(quantity - NVL(restocked_qty,0))`（旧模型支付时已扣锁定，需补回）。
5. 说明：重复执行会在 ALTER 处报错，属预期（与 upgrade_channel_merchant_config.sql 口径一致）。

## 3. API 契约与兼容性影响

| 契约 | 变更 | 兼容性 |
|---|---|---|
| Product JSON（管理端/商城端商品接口） | 新增 soldStock 字段 | 增量 |
| t_inventory_transaction 落水 | ORDER_COMMIT 流水保留但 delta 全 0（支付不搬库存，仅事件标记）；新增 ORDER_SOLD 类型（locked_delta=-qty, sold_delta=+qty） | 增量 |
| InventoryService.restockForRefund | 签名新增 boolean afterReceipt（内部服务，非公共 API） | 仅内部调用方（RefundOrderServiceImpl 两处） |
| 管理端商品列表/详情 | 新增 锁定库存 / 已售库存 展示列 | 增量 |
| 管理端库存流水类型筛选 | 新增 ORDER_SOLD 选项 | 增量 |

迁移/回滚说明：sold_stock/sold_delta 均带 DEFAULT，旧代码对新增列无感知，可直接回滚代码；upgrade 脚本的回填为一次性校正，回滚后无需逆向。

## 4. 后端设计

- `ProductMapper`：新增 `commitSoldStock(id, qty)`（locked-=qty, sold+=qty WHERE locked>=qty）、`releaseSoldStock(id, qty)`（sold-=qty, available+=qty WHERE sold>=qty）；删除孤儿方法 `commitReservedStock`、`restock`。
- `InventoryService`：新增 `convertToSoldOnReceipt(orderNo)`；`commitReservation` 不再搬库存（保留 reservation CAS 与 ORDER_COMMIT 事件流水）；`restockForRefund(refundNo, items, afterReceipt)` 按来源桶分流。
- `ShipmentServiceImpl.doConfirmReceipt`：物流 CAS 之后调用 `inventoryService.convertToSoldOnReceipt(orderNo)`（同事务、同分布式锁）。
- `RefundOrderServiceImpl`：CANCEL_BEFORE_SHIP 受理回补传 `false`；RETURN_AND_REFUND 签收回补传 `true`。

## 5. 幂等设计（多层防线）

1. **物流单 CAS**：DELIVERED→RECEIVED 只可能成功一次，重复确认收货在入口即幂等返回（shipment.status=RECEIVED）。
2. **流水 bizNo 唯一键预检**：结转前按 `ORDER_SOLD:{orderNo}:{orderItemId}` 查重，已存在则跳过（防异常路径重复触发）。
3. **唯一键兜底**：insertTransaction 捕获 DuplicateKeyException 幂等跳过。
4. **CAS 守卫**：`WHERE locked_stock >= qty`（结转）/ `WHERE sold_stock >= qty`（已售回补）防超扣；结转 CAS 失败仅告警跳过、不阻塞收货（兼容旧模型存量在途单）。
5. 退款回补沿用既有 bizNo 预检 + addRestockedQty 上限守卫。

## 6. 验收标准

1. [x] `mvn compile` 通过（2026-09-06，EXIT=0）。
2. [x] payment-demo-react-admin / payment-demo-vue-admin `npm run build` 通过（2026-09-06：React `✓ built in 4.52s`；Vue `DONE Build complete`）。
3. [ ] 全链路账实核对（新订单实测）：下单（available↓/locked↑）→ 支付（数量不变，reservation=COMMITTED，ORDER_COMMIT 流水 delta=0）→ 发货 → 确认收货（locked↓/sold↑，ORDER_SOLD 流水）；重复确认收货幂等（数量不再变化）。**遗留复测项：需重启后端加载新代码后走一遍完整下单-收货链路。**
4. [ ] 退款分流实测：未收货退款回补 locked→available；已收货退货退款回补 sold→available。**遗留复测项：随 6.3 一并复测。**
5. [x] 存量库执行 upgrade_inventory_sold_stock.sql 后账实相符（2026-09-06 经 JDBC 对 192.168.1.200:5236/SYSDBA 实测：7 条语句全 OK；列 sold_stock/sold_delta 到位；商品1 available=489/locked=1/sold=1，与已收货结转 1、在途已支付 1 完全吻合，商品2-4 均为 0）。

## 7. Change Log

- 2026-09-06：创建 planned spec。
- 2026-09-06：实现落地（Product/InventoryTransaction 实体、ProductMapper 新增 commitSoldStock/releaseSoldStock 并移除孤儿 commitReservedStock/restock、InventoryBizType 新增 ORDER_SOLD、InventoryService 增 convertToSoldOnReceipt 并改造 commitReservation/restockForRefund(afterReceipt)、ShipmentServiceImpl 确认收货接入结转、RefundOrderServiceImpl 两处调用分流、双端管理页新增已售库存列与 ORDER_SOLD 流水筛选）；存量库升级与账实核对完成；迁移 implemented，遗留 §6.3/6.4 运行时复测项。
