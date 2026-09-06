# MAPPER_XML_DYNAMIC_SQL_SPEC — 动态条件查询全面迁移至 MyBatis XML 动态 SQL

- 状态：`implemented`（2026-09-06：`mvn compile` 通过；用 MyBatis XMLMapperBuilder 离线解析全部 17 个 XML，接口方法全部绑定，动态 SQL 在全空/全填参数下渲染结果与原 QueryWrapper 语义一致）
- 日期：2026-09-06
- 问题分类：Design change（数据访问层组织方式变更：Java QueryWrapper 动态条件 → XML `<where>/<if>` 动态 SQL），行为保持、无 API 契约变更。
- 需求来源：用户要求"mapper 全面改造成 XML 处理支持动态条件"，经确认范围为**全部动态条件查询**（Java `if` 拼可选条件的查询）迁移至 XML；固定条件查询（selectById、单字段 eq、in、CAS 更新等）保留 MyBatis-Plus BaseMapper/QueryWrapper 现状。

## 1. 背景与问题

项目数据访问层混用两种方式：自定义 SQL（行锁/CAS）已在 `resources/mapper/*.xml`；标准 CRUD 走 MyBatis-Plus BaseMapper。其中**可选筛选条件**的动态查询在 Java 层用 `if + QueryWrapper.eq/like` 拼装，共 6 处：

| # | 调用点 | 表 | 可选条件 |
|---|---|---|---|
| 1 | `CheckoutServiceImpl.listAllOrders`（管理员订单列表） | t_order_info | payStatus、orderStatus、fulfillmentStatus、orderNo(LIKE)、userId、create_time 范围 |
| 2 | `AdminUserController.list`（用户列表分页） | t_user | username LIKE 关键字 + LIMIT/OFFSET |
| 3 | `ProductStockServiceImpl.pageTransactions`（库存流水分页） | t_inventory_transaction | productId、bizType、operation_status + LIMIT/OFFSET |
| 4 | `BillReconcileServiceImpl.listImports`（对账批次列表） | t_bill_import | bill_date |
| 5 | `BillReconcileServiceImpl.listRecords`（账单明细） | t_bill_record | import_id(固定) + record_type |
| 6 | `BillReconcileServiceImpl.listDiscrepancies`（对账差异） | t_bill_reconcile_discrepancy | import_id(固定) + biz_type/discrepancy_type/status |

目标：动态条件统一由 XML mapper 的 `<where>/<if>` 承载，Java 层只负责参数规整与校验。

## 2. 非目标（明确不做）

- 固定条件查询（`selectById`、`eq("order_no",x)`、`in(...)`、CAS UpdateWrapper、LambdaQueryWrapper 单值查询等）**保留现状**——无动态条件，迁 XML 无收益且徒增回归面。
- 不改变任何 HTTP API 路径、请求参数、响应结构、排序与分页语义。
- 不引入 MyBatis-Plus 分页插件（维持现有 count + LIMIT/OFFSET 手动分页方式）。

## 3. 兼容性影响

- **对外 API**：无任何变化（同路径、同参数、同响应）。
- **SQL 语义**：迁移后 SQL 与原 QueryWrapper 生成的 SQL 等价（同列、同操作符、同排序）；模糊匹配沿用"参数两侧加 %"（原 MP `like` 行为），在 Java 层拼 `%keyword%` 后以 `LIKE #{x}` 传入，规避 DM8 CONCAT 参数个数方言差异。
- **分页**：DM8 已验证支持 `LIMIT n OFFSET m`（原代码 `.last("LIMIT ... OFFSET ...")` 运行中），XML 中以 `LIMIT #{limit} OFFSET #{offset}` 占位参数表达；limit/offset 为服务端钳制后的 int/long（1≤size≤100）。
- **count 语句**：新增 count 查询返回 `Long`（原 `selectCount` 返回 Integer，service 层判空逻辑改为基本类型 long）。
- 回滚方式：还原 Java 调用点与删除新增 XML 语句即可，无数据/配置迁移。

## 4. 实现锚点

### 4.1 XML（均位于 resources/mapper/，受 `mybatis-plus.mapper-locations: classpath:mapper/*.xml` 扫描）
- 扩展 `OrderInfoMapper.xml`：新增 `selectAdminOrderList`（7 个可选条件 `<if>`，`order by create_time desc`）。
- 新建 `UserAccountMapper.xml`：`countAdminUsers`、`selectAdminUsers`（username LIKE 可空，order by id asc，`limit #{limit} offset #{offset}`）。
- 新建 `InventoryTransactionMapper.xml`：`<sql id="transactionFilter">` 公共片段 + `countTransactions`、`selectTransactionPage`（productId/bizType/status 可空，order by create_time desc, id desc，LIMIT/OFFSET）。
- 新建 `BillImportMapper.xml`：`selectImportsByDate`（bill_date 可空，order by id desc）。
- 新建 `BillRecordMapper.xml`：`selectRecordsByImport`（import_id 必填、record_type 可空，order by id asc）。
- 新建 `BillReconcileDiscrepancyMapper.xml`：`selectDiscrepanciesByImport`（import_id 必填；biz_type/discrepancy_type/status 可空；order by id asc）。

### 4.2 Mapper 接口
- `OrderInfoMapper` 增加 `selectAdminOrderList(@Param... payStatus,orderStatus,fulfillmentStatus,orderNoLike,userId(Long),startTime(Date),endTime(Date))`。
- `UserAccountMapper`、`InventoryTransactionMapper` 增加 count（返回 Long）+ 分页查询方法。
- `mapper/bill/BillImportMapper`、`BillRecordMapper`、`BillReconcileDiscrepancyMapper` 各增加列表查询方法。

### 4.3 Java 调用点（行为保持）
- `CheckoutServiceImpl.listAllOrders`：保留 userId 数字校验、时间解析（parseFilterTime）、起止倒置校验；orderNo 拼 `%...%` 传 `orderNoLike`；改调 `selectAdminOrderList`。
- `AdminUserController.list`：keyword trim 后拼 `%...%`（空则 null）；调 `countAdminUsers` + `selectAdminUsers`；已移除 QueryWrapper import。
- `ProductStockServiceImpl.pageTransactions`：bizType/status trim 空转 null；调 count + 分页方法（recentLogs 固定条件查询保留 QueryWrapper 现状）。
- `BillReconcileServiceImpl`：listImports/listRecords/listDiscrepancies 的 trim/大写规整保留（recordType、bizType、status 大写；discrepancyType 仅 trim，保持现状），空值转 null 后调 XML 方法；import_id 仍由 `requireByImportNo` 解析；其余固定条件 QueryWrapper 不变。

## 5. 验收标准

1. [x] 后端 `mvn compile` 通过；离线用 MyBatis XMLMapperBuilder 解析全部 17 个 XML 无解析错误，6 个接口的声明方法全部绑定到 MappedStatement。
2. [x] 订单列表：全空参数渲染为 `select * from t_order_info order by create_time desc`；全填参数渲染 7 个条件（pay_status/order_status/fulfillment_status 等值、order_no like、user_id、create_time 闭区间）；userId 数字校验、时间解析/起止倒置校验仍在 Java 层原样保留。
3. [x] 用户列表：count + 分页两条 SQL，username like 可空、id asc、limit/offset；total/page/size/records 响应结构不变。
4. [x] 库存流水：count + 分页共享 `<sql id="transactionFilter">`，product_id/biz_type/operation_status 可选，排序 create_time desc, id desc 不变。
5. [x] 对账：imports（bill_date 可空，id desc）、records（import_id 必填 + record_type 可空，id asc）、discrepancies（import_id 必填 + biz_type/discrepancy_type/status 可空，id asc）；空条件返回全量；大写规整语义与迁移前一致。
6. [x] 固定条件查询未被改动（PaymentApp/Channel LambdaQueryWrapper、退款/支付/库存链路、recentLogs 等保持 QueryWrapper/BaseMapper 原样）。

> 备注：当前工作副本 `payment-demo/src/test` 测试源码缺失，无法运行单测；除 `mvn compile` 外，额外用 MyBatis 离线解析 + BoundSql 渲染（全空/全填两组参数）验证 XML 与动态条件。DM8 的 `LIMIT n OFFSET m` 为迁移前已在使用的方言（原 `.last("LIMIT ... OFFSET ...")`），XML 改为占位参数形式。
