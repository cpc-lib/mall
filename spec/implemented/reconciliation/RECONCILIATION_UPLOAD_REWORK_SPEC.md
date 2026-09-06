# 微信交易账单上传式对账重构 - 规格（spec/implemented 账本）

> 本文件为项目规格账本（AGENTS.md Spec Reconciliation Rules），工作流产物与任务队列见
> `.trae/specs/reconciliation-upload-rework/spec.md` 与 `tasks.md`。
> 状态：**implemented**（2025-07 落地；后端单测 17 用例全过、双前端 build 通过、合约测试 PASS）。
> 设计变更记录：实施期间依据微信支付 v3《交易账单详细说明》（doc 4013080599）补充「账单种类
> ALL/SUCCESS/REFUND」维度（见下文「账单种类设计」一节）。

## 分类

- 问题类型：Design change（对账工作流重构）+ Public API/compatibility impact（旧 `/api/reconciliation/**` 路由、旧对账三表、MQ 队列/配置键移除，新路由/新表/上传接口新增；React/Vue 对账页面重写）。
- 兼容性影响：旧对账接口与页面替换为不兼容的新契约；旧三表 `t_reconciliation_batch/detail/discrepancy` 不再创建（DROP 守卫块保留以清理遗留库）；RabbitMQ `payment.reconciliation.*` 交换器/队列不再声明；`payment.reconciliation.*` 配置键移除。回滚方式：还原代码并重新执行旧版初始化 SQL。

## 需求摘要

废弃「自动拉取账单 + MQ 异步 + 定时调度 + 渠道策略」旧对账模块，重做为：管理员从微信支付商户平台下载交易账单 CSV → 上传账单文件 → 解析入库（t_bill_import / t_bill_record）→ 自动对账（账单支付行 vs t_payment_info、账单退款行 vs t_refund_info，差异落 t_bill_reconcile_discrepancy）。全链路幂等：文件 hash 唯一、账单日+账单种类唯一、差异唯一约束、分布式锁串行化、已对账批次重复对账直接返回现有结果。

非目标：支付宝对账、资金账单（fundflowbill）、服务端自动拉单/MQ/定时任务；下载账单功能与退款状态主动同步接口（`/api/refund-info/reconcile/{orderNo}`）保持不变。

## 账单种类设计（依据微信 v3 文档 4013080599）

微信交易账单分三种，列序各不相同，解析器按表头列名自适应：

| 种类 | 内容 | 列数 | 对账范围 |
|---|---|---|---|
| ALL | 支付成功行 + 退款行 + 付款码撤销行 | 27 | 支付 + 退款双向 |
| SUCCESS | 仅支付成功行 | 20 | 仅支付方向 |
| REFUND | 仅退款行（含退款申请/成功时间列） | 29 | 仅退款方向 |

入库与互斥规则：

- `t_bill_import.bill_kind` 记录种类；唯一约束 `uk_bill_import_channel_date (channel_code, bill_type, bill_date, bill_kind)`。
- 同一账单日：允许 SUCCESS 与 REFUND 各一份（分别对账支付/退款方向）；ALL 与 SUCCESS/REFUND 互斥（ALL 已含全部记录，混传会导致重复/误报）；同种类重复上传拒绝。
- 对账范围按种类收窄：SUCCESS 账单完全不扫描退款方向、REFUND 不扫描支付方向，避免账单未覆盖方向被误报 LOCAL_ONLY。
- 文件 hash 幂等优先于种类校验：同一文件重传直接返回原批次。

## 验收标准与落地证据

- AC-1 rule：旧对账模块 39 个 Java 文件、3 个 mapper XML、reconciliation-init.sql、旧配置键移除，编译通过。证据：`mvn -q compile`。
- AC-2 rule：payment_demo.sql 旧三表仅保留 DROP 守卫块；新增 t_bill_import / t_bill_record / t_bill_reconcile_discrepancy（列/唯一约束/索引/注释/更新时间触发器齐备，纯 DM8 语法），t_bill_import 含 bill_kind 列与四元组唯一约束。证据：`payment-demo/env/sql/dm8/payment_demo.sql`。
- AC-3 rule：微信账单 CSV 解析器单测覆盖 ALL/SUCCESS/REFUND 三种表头、支付行/退款行/汇总行/坏行/空文件。证据：`WxTradeBillParserTest` 5 用例。
- AC-4 rule：上传后自动对账，八类差异（PAY/REFUND × CHANNEL_ONLY/LOCAL_ONLY/AMOUNT_MISMATCH/STATUS_MISMATCH）正确命中；退款中间态（PROCESSING）不判差异。证据：`BillReconcileServiceIdempotencyTest` 12 用例。
- AC-5 rule：同文件重传、同日同种类重传、ALL/SUCCESS/REFUND 互斥、重复 reconcile、并发上传（锁）场景无重复数据。证据：同上 12 用例（含锁失败转译、种类互斥 3 场景）。
- AC-6 rule：批次/流水/差异查询与差异标记已处理接口契约正确。注：项目无 MyBatis-Plus 分页拦截器，遵循项目既有约定返回全量 List（账单按天、数据量小），非 IPage。证据：`ReconciliationController` + 合约测试。
- AC-7 rule：React/Vue 对账页对等重写（上传区、统计卡、批次表含账单种类列、流水弹窗、差异弹窗、标记处理弹窗），菜单仅管理员可见，build 与合约测试通过。证据：`npm run build` 双端通过；`python tests/frontend_backend_contract_test.py` PASS。
- AC-8 rule：characterization 测试套件通过，下载账单链路零改动。证据：Task 10 回归（mvn characterization 套件 + git diff 复核下载链路）。
- AC-9 rubric：对账结果分类清晰、中文标签、渠道/本地金额状态快照并列、批次可追溯。

## 实现锚点

- SQL：`payment-demo/env/sql/dm8/payment_demo.sql`（t_bill_import / t_bill_record / t_bill_reconcile_discrepancy）
- 后端：
  - 控制器：`payment-demo/src/main/java/cc/ivera/controller/ReconciliationController.java`（基路径 `/api/reconciliation`：POST /bill/upload、POST /imports/{importNo}/reconcile、GET /imports、GET /imports/{importNo}、GET /imports/{importNo}/records、GET /imports/{importNo}/discrepancies、POST /discrepancies/{id}/resolve）
  - 服务：`cc.ivera.service.bill.BillReconcileService` / `cc.ivera.service.impl.bill.BillReconcileServiceImpl`
  - 解析器：`cc.ivera.service.bill.parser.WxTradeBillParser` / `ParsedBill`
  - 实体：`cc.ivera.entity.bill.BillImport` / `BillRecord` / `BillReconcileDiscrepancy`
  - Mapper：`cc.ivera.mapper.bill.*`（纯 BaseMapper，无 XML）
  - 枚举：`cc.ivera.enums.bill.BillImportStatus` / `BillRecordType` / `BillDiscrepancyType` / `BillDiscrepancyStatus` / `BillType` / `BillKind`
  - VO/DTO：`cc.ivera.vo.bill.*`、`cc.ivera.dto.bill.ResolveDiscrepancyRequest`
  - 锁：`cc.ivera.lock.DistributedLockTemplate`，key=`bill-reconcile:WXPAY:{billDate}`，等待 5s/租期 60s
- 前端：
  - React：`payment-demo-react/src/api/reconciliation.js`、`payment-demo-react/src/pages/Reconciliation.jsx`
  - Vue：`payment-demo-vue/src/api/reconciliation.js`、`payment-demo-vue/src/views/Reconciliation.vue`
  - 下载链路保留：两端 `src/api/bill.js`（downloadBillWxPay/downloadBillAliPay）、Download 页面、`.bill-section` 样式
- 测试：
  - `payment-demo/src/test/java/cc/ivera/bill/WxTradeBillParserTest.java`（5 用例）
  - `payment-demo/src/test/java/cc/ivera/bill/BillReconcileServiceIdempotencyTest.java`（12 用例）
  - `tests/frontend_backend_contract_test.py`（对账契约段重写：新端点/新枚举/旧契约残留断言）

## 关键行为约定

- 金额：账单单位元，解析时 BigDecimal movePointRight(2) 转分（退款取绝对值），与本地 payer_total/refund（分）比较。
- 账单字段值前单个反引号前缀清洗；退款单号在 REFUND 账单下标 16/17、ALL 账单下标 14/15；REVOKED 撤销行无退款单号回退订单号；文件尾汇总段跳过；坏行计数跳过不中断。
- LOCAL_ONLY 反查：按账单日 [atStartOfDay, nextDay)（Asia/Shanghai）扫描本地 SUCCESS 记录。
- 事务：事务1 入批次+流水；事务2 对账；失败标 FAILED（error_message 截 900），批次保留可重跑；重跑先按 import_id 删差异再重算；RECONCILED 重对账幂等直返。
- 差异 uk(import_id, discrepancy_type, biz_type, biz_no)；resolve 必填备注（@NotBlank/@Size 500），记录 resolved_by/resolved_at/remark，状态 OPEN→RESOLVED。
- multipart 上限：spring.servlet.multipart max-file-size=10MB / max-request-size=12MB。
