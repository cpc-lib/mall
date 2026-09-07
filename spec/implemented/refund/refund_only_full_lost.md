# 仅退款整单全额退 + 货损核销 SPEC

## 背景与问题

用户申请「仅退款」（REFUND_ONLY）时，当前交互要求逐商品填写退款数量，属于「部分退」模型。
业务方希望仅退款简化为**一键整单全额退**：用户不填数量，系统直接退订单剩余全部款项；
商品不退回，库存计入**丢失库存（lost_stock）**，并写库存流水，全程幂等。

## 目标行为

| 退款类型 | 用户操作 | 退款金额 | 库存处理 |
|----------|----------|----------|----------|
| 未发货取消 CANCEL_BEFORE_SHIP | 一键取消（现状） | 整单 | locked → available（回补，现状） |
| **仅退款 REFUND_ONLY（已发货 SHIPPED）** | **一键整单退，不填数量** | **整单剩余全额** | **locked → lost（货损）** |
| **仅退款 REFUND_ONLY（已收货 RECEIVED）** | **一键整单退，不填数量** | **整单剩余全额** | **sold → lost（货损）** |
| 退货退款 RETURN_AND_REFUND | 填数量（现状保留） | 按数量 | sold → available（签收质检后回补，现状） |

统一语义：**仅退款 = 不退货 = 货损**。货不回仓，按当前库存所在桶（未收货在 locked、已收货在 sold）核销至 lost。

## 设计决策

1. **服务端权威补全数量**：REFUND_ONLY 申请允许 items 为空；后端在 `doCreate` 中按订单全部明细的剩余可退数量（`RefundPolicy.refundableQty`）自动构建明细，金额由尾差规则计算（退完剩余全部 → 剩余可退金额），即整单全额。前端只表达「整单仅退款」意图，不传数量。
2. **货损按履约状态分流来源桶**：
   - SHIPPED（未收货，货仍在锁定桶）：`locked -= qty, lost += qty`
   - RECEIVED（已收货，货已结转售出桶）：`sold -= qty, lost += qty`
3. **幂等**：沿用库存流水 `biz_no = REFUND_LOST:refundNo:itemId` 唯一键 + 插入前 selectCount 预检；明细 `restocked_qty` 原子累加（上限守卫 `restocked+qty ≤ refunded+frozen`）防超核销；数量桶变更均为条件 UPDATE（`WHERE locked>=qty` / `WHERE sold>=qty`），affectedRows=0 抛错回滚。
4. **退货退款不变**：RETURN_AND_REFUND 仍需用户填数量、签收质检后回补 available。

## 实现锚点

### 后端
- `mapper/ProductMapper.java` + `resources/mapper/ProductMapper.xml`：新增 `writeOffSoldLostStock`（sold→lost）。
- `service/InventoryService.java` / `service/impl/InventoryServiceImpl.java`：`writeOffLostForRefund(refundNo, items, afterReceipt)`，按 afterReceipt 分流 locked/sold 来源桶，流水记 REFUND_LOST（lost_delta=+qty，来源桶 delta=-qty）。
- `dto/refund/RefundApplyRequest.java`：items 去掉 `@NotEmpty`（REFUND_ONLY 可空；RETURN_AND_REFUND 仍由服务端校验非空）。
- `service/impl/RefundOrderServiceImpl.java`：
  - 抽取 `buildFullRefundItems(orderNo)`（复用原 doCancelPaidOrder 的整单明细构建）。
  - `doCreate`：REFUND_ONLY 且 items 为空时自动补全整单明细。
  - `prepareAccept`：REFUND_ONLY 受理时按 SHIPPED/RECEIVED 调 `writeOffLostForRefund(..., afterReceipt)`。

### 前端
- `user-ui/src/pages/OrdersV2.jsx`：退款弹窗中 REFUND_ONLY 选中时隐藏数量输入、显示「整单全额退款，商品不退回（计入货损）」，提交 items 传空数组；CANCEL/RETURN 类型保留数量输入。

## 兼容性影响

- **API**：`POST /api/refund-applies` 的 `items` 字段对 REFUND_ONLY 变为可选（传空/不传均可）；对 RETURN_AND_REFUND 仍必填（服务端校验「至少选择一个退款商品」）。旧前端传 items 的 REFUND_ONLY 请求仍兼容（按传入数量处理）。
- **DB**：无新增表/列（`lost_stock`、`lost_delta` 已由 `upgrade_add_lost_stock.sql` 提供）。
- **库存口径**：RECEIVED 状态仅退款由「不动库存」变为「sold→lost」。此为本次明确的业务规则变更（仅退款=货损）。

## 验收标准

- [x] 已发货未收货订单：一键仅退款 → 管理员受理 → t_product `locked_stock` 减少、`lost_stock` 增加；流水写 REFUND_LOST（locked_delta=-qty, lost_delta=+qty）。
- [x] 已收货订单：一键仅退款 → 受理 → `sold_stock` 减少、`lost_stock` 增加；流水 REFUND_LOST（sold_delta=-qty, lost_delta=+qty）。
- [x] 仅退款不填数量，退款金额 = 订单剩余可退全额。
- [x] 重复受理/重复回调不产生重复货损（biz_no 幂等：selectCount 预检 + uk_inventory_biz_no 唯一键兜底）。
- [x] 退货退款仍走填数量 + 签收回补 available，不受影响（items 非空校验保留在 createItemsAndFreeze）。
- [x] 后端 mvn compile 通过；user-ui npm run build 通过。

## 实施记录（2026-09-07）

- 后端：`ProductMapper.writeOffSoldLostStock`（sold→lost）；`InventoryService.writeOffLostForRefund(refundNo, items, afterReceipt)` 分流；`RefundApplyRequest.items` 去 @NotEmpty；`RefundOrderServiceImpl` 抽取 `buildFullRefundItems`，doCreate 对 REFUND_ONLY 空明细自动补全整单，prepareAccept 对 SHIPPED/RECEIVED 均核销货损。
- 前端：`user-ui/src/pages/OrdersV2.jsx` 仅退款弹窗隐藏数量输入、Alert 提示货损、items 传空、按钮改「申请退款」。
- 验证：后端 `mvn compile` 通过；user-ui `npm run build` 通过（OrdersV2 chunk 25.05 kB）。
- DB：无新增列（lost_stock/lost_delta 已由 upgrade_add_lost_stock.sql 提供）。
