# 仅退款（未收货）货损计入丢失库存 SPEC

> 状态：已实施（第一阶段，2026-09-07）。本阶段确立库存第四桶 `lost_stock` 与 SHIPPED 场景 locked→lost 核销。
> 后续由 [refund_only_full_lost.md](./refund_only_full_lost.md) 扩展：REFUND_ONLY 改为一键整单全额退（不填数量），并补充 RECEIVED 场景 sold→lost 核销。

## 背景

订单已发货（SHIPPED）未确认收货时，库存仍在 `locked` 桶。用户申请 `REFUND_ONLY`（仅退款，不退货），管理员受理后钱原路退回，但**货物不要求退回**（在途货损/拒收丢件），因此不能归还可用库存，应从锁定中核销为「丢失库存」。

## 库存模型（新增第四桶）

`t_product` 库存四桶恒等：`available + locked + sold + lost = 入库总量`

| 桶 | 含义 |
|----|------|
| available_stock | 可售 |
| locked_stock | 下单预占未结转 |
| sold_stock | 确认收货已售 |
| **lost_stock** | **丢失/货损（仅退款未收货核销）** |

## 退款类型 × 库存动作（最终规则）

| 退款类型 | 履约状态 | 库存动作 |
|----------|----------|----------|
| CANCEL_BEFORE_SHIP | WAIT_SHIP | 受理即回补：locked → available |
| REFUND_ONLY | SHIPPED（未收货） | **受理即核销货损：locked → lost** |
| REFUND_ONLY | RECEIVED（已收货） | 不动库存（货留用户，瑕疵补偿，货权已转移在 sold） |
| RETURN_AND_REFUND | RECEIVED | 签收质检后回补：sold → available |
| PRICE_ADJUSTMENT / 系统冲正 | 任意 | 不动库存 |

## 数据结构变更（DB schema）

- `t_product` 新增 `lost_stock INT DEFAULT 0 NOT NULL`
- `t_inventory_transaction` 新增 `lost_delta INT DEFAULT 0 NOT NULL`
- 库存流水新增类型 `REFUND_LOST`
- 存量库升级：
  ```sql
  ALTER TABLE t_product ADD COLUMN lost_stock INT DEFAULT 0 NOT NULL;
  ALTER TABLE t_inventory_transaction ADD COLUMN lost_delta INT DEFAULT 0 NOT NULL;
  ```

## 幂等与防重

- 流水 biz_no：`REFUND_LOST:refundNo:itemId`（uk_inventory_biz_no 唯一键兜底）
- 复用 `addRestockedQty` 累加 order_item.restocked_qty：① 防超核销守卫（restocked+qty ≤ refunded+frozen）；② 确认收货结转数量 `quantity - restocked_qty` 自动跳过已核销部分，避免部分退款时 commitSoldStock 整笔 CAS 失败。

## 验收标准

- [x] REFUND_ONLY + SHIPPED 受理后 `locked_stock -= qty`、`lost_stock += qty`，available 不变
- [x] REFUND_ONLY + RECEIVED 受理后库存不变
- [x] REFUND_LOST 流水落库，biz_no 幂等
- [x] 部分退款后确认收货，未退部分正常 locked → sold，已核销部分不重复结转（restocked_qty 扣减）
- [x] 后端编译通过
