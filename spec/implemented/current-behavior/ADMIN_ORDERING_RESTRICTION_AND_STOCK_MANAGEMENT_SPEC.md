# Admin Ordering Restriction And Product Stock Management Spec

## 0. Metadata

- Status: implemented
- Domain: current-behavior (auth rule + backend endpoints + both frontends)
- Updated: 2026-09-05
- Owner: TBD
- Related work: 管理员禁止下单，仅普通用户可交易；管理员新增商品库存管理能力；React 头部登出入口补齐
- Issue classification: Design change
- Impact scope: public API additions, auth interceptor rule, React/Vue navigation and admin console

## 1. Background

Both frontends had no top-right logout entry, change-password stayed hidden inside the account page, admins could go through the full ordering flow (cart/checkout/pay), and there was no UI or backend capability for admins to manage product stock directly. This spec defines the target rules.

## 2. Contract

### 2.1 Role rule: admin does not order

- `ROLE_ADMIN` is rejected with HTTP 403 and message `管理员账号不支持购物车与下单操作` for every request under `/api/cart` and `/api/checkout`.
- The rule is enforced centrally in `AuthInterceptor` (not per controller), so it cannot be bypassed by calling the API directly.
- Normal users (`ROLE_USER`) keep full access to cart, checkout, payment, refund application flows.
- Admin-only surfaces (`/api/admin/**`, refund accept/reject, payment config, reconciliation, download bill) are unaffected.

### 2.2 Admin product stock management API (new)

All under `/api/admin/products` (admin required by existing interceptor rule):

- `GET /api/admin/products` — list all products ordered by `id` asc, including `DISABLED` ones. Response: `R<List<Product>>`.
- `POST /api/admin/products/{id}/stock` — body `{ "delta": int }`. Delta must be non-zero. Stock is updated atomically via `stock = stock + delta` with guard `stock + delta >= 0`; when the guard fails the API throws `库存扣减后不能为负数` (unknown product id throws `商品不存在`). Response: `R<Product>` with the updated product.
- `POST /api/admin/products/stock/batch` — body `{ "items": [ { "productId": long, "delta": int }, ... ] }`（逐商品明细，可补货/扣减混批）. Processed **per item**: each item runs the same atomic unit as the single endpoint (CAS update + `MANUAL_ADJUST` transaction, own transaction); a failing item (商品不存在 / 库存扣减后不能为负数 / 调整量为 0) does NOT roll back already-succeeded items **and writes a FAILED audit row** (`operation_status=FAILED`, `available_delta=0`, `error_message` = `申请调整 {delta} 被拒绝：{reason}`) into `t_inventory_transaction`. Response: `{ total, successCount, failCount, results: [{ productId, delta, success, stock?, message }] }`. The single endpoint records FAILED rows the same way.
- `GET /api/admin/stock/transactions?page=&size=&productId=&bizType=&status=` — paginated inventory transaction audit query over `t_inventory_transaction` (create_time desc, id desc). `page` clamped >= 1, `size` clamped to 1..100; `productId`/`bizType`/`status`(SUCCESS|FAILED) optional filters. Response: `{ total, page, size, records: [{id, bizNo, operationType, operationStatus, orderNo, availableDelta, lockedDelta, errorMessage, createTime}] }`. Implemented with manual `count + LIMIT/OFFSET` (DM8-native) to avoid pagination-plugin dialect dependence.
- Schema (V4): `t_inventory_transaction` gains `operation_status VARCHAR(16) DEFAULT 'SUCCESS' NOT NULL` and `error_message VARCHAR(500)` plus index `idx_inventory_tx_status_time(operation_status, create_time)`. Trading-chain writers keep the column default (SUCCESS). Idempotent upgrade: `env/sql/dm8/upgrade_stock_audit_v4.sql`, auto-detected by `env/scripts/dm8/init-dm8-sql.sh --if-missing` (V4-only / V3+V4 / V1→V2+V3+V4 modes).
- Dedicated maintenance page `/admin/stock-maintenance` (React `StockMaintenance.jsx`, Vue `StockMaintenance.vue`, admin-only): 导入记录卡片（模板下载/Excel 导入/确认入库 + 记录表行选中）、Luckysheet Excel 编辑弹窗、逐条结果明细 + 分页库存流水日志（product/type/status filters）。Entry button `批量库存维护` in AdminConsole header of both frontends.
- Excel import with backend-stored files + persisted import records (same page, both frontends; SheetJS `xlsx@0.18.5` parses client-side, Luckysheet 2.1.13 provides in-browser spreadsheet editing): 下载模板 generates `批量库存调整模板.xlsx` (columns `商品ID | 商品名称 | 调整量`, pre-filled with all current products); 导入 Excel parses the first sheet, matches rows by first column against existing products (template header row skipped), ignores rows with empty delta, rejects unknown IDs / non-integer or 0 deltas with per-row issues; the workbook is re-encoded to standard xlsx (base64) and stored on the backend disk directory (`stock.import.dir`, default `./upload/stock-import`, file named `{recordId}.xlsx`) while a PENDING import record is created — a sheet with no valid row generates no record. Excel import alone never changes stock.
- Stock import records (V5): `t_stock_import` stores `file_name`, `item_count`, `status` (`PENDING`|`CONFIRMED`), `items_json` (明细快照 `[{"productId":13,"delta":50},...]`), `confirm_time`. APIs under `/api/admin/stock` (admin required): `POST /imports` body `{fileName, fileBase64, items}` creates a PENDING record + stores the file (validates file ≤ 2MB & legal base64; per item: 商品存在、调整量非 0 整数; `items_json` 超 4000 字符拒绝; 文件先校验、明细后校验); `GET /imports` lists latest 50 (create_time desc, `items` parsed); `GET /imports/{id}/file` returns the stored Excel (base64) for the frontend JS editor; `PUT /imports/{id}/file` body `{fileBase64, items}` saves edits — 仅 PENDING 可改（状态守卫 + CAS 双重防护，非 PENDING 抛 `该导入记录已确认入库，明细不可再修改`），覆盖后端文件并同步刷新 `item_count`/`items_json`，保证文件与明细一致; `POST /imports/{id}/confirm`（无请求体）— 先 CAS `PENDING→CONFIRMED`（防并发/重复执行，非 PENDING 抛 `该导入记录已确认入库，不能重复执行`），再按记录已保存明细逐条执行与批量调整相同的原子单元（明细为空抛 `导入明细为空，请先在编辑中保存明细后再确认入库`），失败条目落 FAILED 流水不回滚已成功条目，返回体与批量调整一致. 幂等升级：`env/sql/dm8/upgrade_stock_import_v5.sql`（直接 DDL），由 `env/scripts/dm8/init-dm8-sql.sh --if-missing` 自动检测执行（V5-only / V4+V5 / V1→V2+V3+V4+V5 分级模式）。
- Maintenance page 导入记录卡片 is the primary workflow surface (both frontends): toolbar buttons 下载模板 / 导入 Excel / 确认入库，按钮左侧实时显示当前选中记录（`已选：#{id} {fileName}（{itemCount} 条）` / `未选择记录`）; record table (＋/选择radio/记录ID/文件名/明细条数/状态(待确认|已入库)/导入时间/操作) with **radio 单选列 + 行点选双通道选中**（两者同步更新同一状态，仅 PENDING 可选中，已入库行 radio 禁用、点选无效果，选中行高亮）。**行首 ＋ 图标与操作列 `选择并编辑`/`编辑 Excel` 按钮均新开浏览器页签**打开全屏 Excel 编辑器路由 `/#/admin/stock-edit/{id}`（React `StockExcelEditor.jsx` / Vue `StockExcelEditor.vue`，RequireAdmin 守卫；该路由不渲染公共头/底，Luckysheet 铺满整页，顶栏提供 返回（关闭页签）/保存修改，非 PENDING 记录显示「已入库，只读核对」并禁用保存）；原 960px 弹窗编辑器与行内展开明细预览已移除。编辑页保存经 SheetJS 校验 + `PUT /imports/{id}/file`（invalid filled rows block the save; empty rows skipped），库存维护页监听 window focus 自动刷新记录列表。确认入库 acts on the selected PENDING record after a confirm dialog and calls `POST /imports/{id}/confirm`（未点选记录时提示 `请先点选一条导入记录`，成功后清除选中并刷新记录/库存/流水）; results render in the 批量结果 card. The former full-products manual delta grid (直接批量调整入口) was REMOVED from the page — batch adjustments now flow exclusively through import records (the `POST /api/admin/products/stock/batch` API itself remains, reused internally by record confirmation's per-item unit).
- `POST /api/admin/products/{id}/status` — body `{ "productStatus": "ENABLED" | "DISABLED" }`; other values throw `商品状态仅支持 ENABLED / DISABLED`. Response: `R<Product>`.
- These endpoints do not touch order, payment, or refund state; stock restocking through MQ consumers keeps its existing separate path.

### 2.3 Frontend behavior (React and Vue, kept consistent)

- Header top-right: logged-in users see `退出登录` entry. Clicking it calls `POST /api/auth/logout` with the refresh token, then clears the local auth store and routes to the login page, even if the logout call fails.
- Admin users do not see 购物车 / 我的订单 / 我的退款 nav entries, and product cards show no 加入购物车 / 去购物车 buttons (a hint points to the admin console instead).
- Admin console gains a first tab 商品库存管理: product table (id/title/price/stock/status), per-row stock delta input with 确认调整, and an 上架/下架 toggle button.

## 3. Acceptance Criteria

- [x] Admin requests to `/api/cart/**` and `/api/checkout/**` are rejected 403 by the interceptor; normal users pass. Locked by `AuthInterceptorRoleRuleTest`.
- [x] `AdminProductController` implements list / stock adjust / status switch with the guards above.
- [x] React header shows 退出登录 when logged in; admins see no ordering nav entries.
- [x] Vue header shows 退出登录 when logged in; admins see no ordering nav entries.
- [x] Both admin consoles expose the 商品库存管理 tab backed by the new API.
- [x] Characterization suites still pass; both frontend builds succeed.

## 4. Implementation Anchors

| Area | Anchor |
|---|---|
| Interceptor admin-shopping block rule | `payment-demo/src/main/java/cc/ivera/security/AuthInterceptor.java` (`isUserOnlyShopping`) |
| Admin product API | `payment-demo/src/main/java/cc/ivera/controller/AdminProductController.java` |
| Stock adjust audit bizType | `payment-demo/src/main/java/cc/ivera/enums/InventoryBizType.java` (`MANUAL_ADJUST`) |
| Stock adjust service (atomic unit / recent logs / paging) | `payment-demo/src/main/java/cc/ivera/service/ProductStockService.java`, `payment-demo/src/main/java/cc/ivera/service/impl/ProductStockServiceImpl.java` |
| Batch adjust endpoint + DTO | `payment-demo/src/main/java/cc/ivera/controller/AdminProductController.java` (`POST /stock/batch`), `payment-demo/src/main/java/cc/ivera/dto/admin/ProductStockBatchAdjustRequest.java` |
| Paged transactions endpoint | `payment-demo/src/main/java/cc/ivera/controller/StockAdminController.java` (`GET /transactions`) |
| Stock import endpoints + DTO | `payment-demo/src/main/java/cc/ivera/controller/StockAdminController.java` (`POST /imports`, `GET /imports`, `GET/PUT /imports/{id}/file`, `POST /imports/{id}/confirm`), `payment-demo/src/main/java/cc/ivera/dto/admin/StockImportCreateRequest.java`, `payment-demo/src/main/java/cc/ivera/dto/admin/StockImportFileSaveRequest.java` |
| Stock import service (create/list/confirm + CAS 防重复) | `payment-demo/src/main/java/cc/ivera/service/StockImportService.java`, `payment-demo/src/main/java/cc/ivera/service/impl/StockImportServiceImpl.java` |
| Excel 文件磁盘存储 | `payment-demo/src/main/java/cc/ivera/storage/StockImportFileStore.java` (`stock.import.dir`, 默认 `./upload/stock-import/{id}.xlsx`) |
| Luckysheet 静态资源（双端本地托管） | `payment-demo-react/public/luckysheet/`、`payment-demo-vue/public/luckysheet/`（含 index.html 引用） |
| Stock import entity/mapper | `payment-demo/src/main/java/cc/ivera/entity/StockImport.java`, `payment-demo/src/main/java/cc/ivera/mapper/StockImportMapper.java` |
| V5 schema + idempotent upgrade | `payment-demo/env/sql/dm8/payment_demo.sql` (`t_stock_import`), `payment-demo/env/sql/dm8/upgrade_stock_import_v5.sql`, `payment-demo/env/scripts/dm8/init-dm8-sql.sh` (V5 分级检测) |
| Import record UI (React/Vue) | `payment-demo-react/src/pages/StockMaintenance.jsx`, `payment-demo-vue/src/views/StockMaintenance.vue` |
| Stock import tests | `payment-demo/src/test/java/cc/ivera/service/impl/StockImportServiceTest.java`, `payment-demo/src/test/java/cc/ivera/controller/StockImportControllerTest.java`, `payment-demo/src/test/java/cc/ivera/database/Dm8MigrationContractTest.java` |
| Stock audit tests | `payment-demo/src/test/java/cc/ivera/service/impl/ProductStockServiceTest.java`, `payment-demo/src/test/java/cc/ivera/controller/AdminProductStockBatchTest.java` |
| React product detail (log table with 调整量) | `payment-demo-react/src/pages/ProductDetail.jsx` |
| Vue product detail (log table with 调整量) | `payment-demo-vue/src/views/ProductDetail.vue` |
| Request DTOs | `payment-demo/src/main/java/cc/ivera/dto/admin/ProductStockAdjustRequest.java`, `payment-demo/src/main/java/cc/ivera/dto/admin/ProductStatusRequest.java` |
| Rule tests | `payment-demo/src/test/java/cc/ivera/security/AuthInterceptorRoleRuleTest.java` |
| React header/logout/nav | `payment-demo-react/src/components/AppHeader.jsx` |
| React home gating | `payment-demo-react/src/pages/Home.jsx` |
| React stock tab + API | `payment-demo-react/src/pages/AdminConsole.jsx`, `payment-demo-react/src/api/adminStock.js` |
| Vue header/nav | `payment-demo-vue/src/components/AppHeader.vue` |
| Vue home gating | `payment-demo-vue/src/views/index.vue` |
| Vue stock tab + API | `payment-demo-vue/src/views/AdminConsole.vue`, `payment-demo-vue/src/api/adminStock.js` |

## 5. Compatibility Impact

- New public API routes `GET /api/admin/products`, `POST /api/admin/products/{id}/stock`, `POST /api/admin/products/{id}/status` (additive only; admin-authenticated).
- V5 evolution (same-day, backend not yet redeployed after initial V5): `POST /api/admin/stock/imports` now requires `fileBase64` (file stored to disk); `POST /api/admin/stock/imports/{id}/confirm` no longer takes a request body (executes the record's saved 明细); new `GET/PUT /api/admin/stock/imports/{id}/file`. No DB schema change beyond V5's `t_stock_import`. Rollback: revert to record-only flow; stored files under `upload/stock-import/` are inert artifacts.
- Behavior change: the 批量库存维护 page no longer offers the direct full-products delta grid — batch adjustments go exclusively through import records. The `POST /api/admin/products/stock/batch` API remains available (unchanged contract) for API consumers.
- Behavior change: admin tokens can no longer call `/api/cart/**` and `/api/checkout/**` (previously allowed). Rollback: remove the `isUserOnlyShopping` check in `AuthInterceptor`; no data migration involved.
- Response text, status values, DB schema, event names, provider callbacks: unchanged.
- Migration/rollback notes: none needed for existing data; the rule is stateless.

## 6. Verification

```powershell
mvn "-Dtest=AuthInterceptorRoleRuleTest,PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test
npm run build   # in payment-demo-react and payment-demo-vue
```

Result on 2026-09-05 (含 V5 导入记录 + 后端文件存储 + Luckysheet 编辑): backend 247 tests green across 29 suites (incl. StockImportServiceTest 10 cases / StockImportControllerTest 6 cases / Dm8MigrationContractTest V5 assertions / characterization suites), React and Vue production builds succeeded (Luckysheet assets bundled into both dists).

## 7. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-09-05 | implemented | Admin ordering restriction, admin product stock management API + admin console tab, top-right logout for both frontends. | User request: react端没有登出与修改密码的功能，管理员不需要下单功能 |
| 2026-09-05 | implemented | 修复库存操作日志缺失：手动调整库存落 `MANUAL_ADJUST` 流水（t_inventory_transaction，同事务）；商品详情日志改查库存流水表并新增“调整量”列（原实现按 biz_no=productId 查 t_stock_operation_log，受唯一约束限制最多 1 条且 V2 不再写入）。 | User request: 库存操作日志的功能没有实现，调整了库存但没有记录 |
| 2026-09-05 | implemented | 批量库存调整 `POST /api/admin/products/stock/batch`（逐商品明细、逐条处理返回成功/失败明细、单品原子单元复用 adjustOne）；库存流水分页接口 `GET /api/admin/stock/transactions`（count + LIMIT/OFFSET 手动分页，支持商品/类型过滤）。决策：明细列表入参 / 逐条不回滚 / 仅后端分页接口。 | User request: 批量添加商品库存/扣减库存并保存日志，库存日志表支持分页查询 |
| 2026-09-05 | implemented | 库存维护页 `/admin/stock-maintenance`（React/Vue，管理台头部入口）：批量调整编辑器 + 逐条结果明细 + 库存流水分页（商品/类型/状态过滤）。V4 schema：t_inventory_transaction 增加 operation_status/error_message 列与 idx_inventory_tx_status_time 索引；调整失败落 FAILED 流水（available_delta=0，错误信息含申请调整量），幂等升级脚本 upgrade_stock_audit_v4.sql + init-dm8-sql.sh V4 分级检测。 | User request: 批量修改商品库存功能 + 错误记录写入批量库存日志 + 独立维护页面 |
| 2026-09-05 | implemented | 修复 V4 升级未生效（运行时报 无效的列名[operation_status]）：upgrade_stock_audit_v4.sql 由 PL/SQL 守卫块改为直接 DDL 语句（DM Manager 对多 ';' + '/' 的 PL/SQL 块执行不可靠，历史教训）；幂等改由 init-dm8-sql.sh V4 签名守卫承担，全新库模式跳过 upgrade_* 脚本（payment_demo.sql 已含最新 schema）。迁移契约测试锁定升级脚本无 PL/SQL。payment_demo.sql 本身无误，未改动。 | User request: 修复字段问题（无效的列名 operation_status）并给手动修表 SQL |
| 2026-09-05 | implemented | 批量库存维护页编辑器改造为全商品单表格（行内 ±调整量、留空跳过、一次提交）+ Excel 导入前置流程：下载模板（预填全部商品）/ 导入 Excel（SheetJS 前端解析，按商品ID匹配，非法行列出问题）→ 表格核对编辑 → 确认入库（复用现有批量接口，导入本身不改库存）。决策：前端解析/按ID匹配/模板预填商品清单。 | User request: Excel 说明补货商品 → 管理员编辑 → 确认入库才执行 |
| 2026-09-05 | implemented | Excel 导入记录持久化（V5）：`t_stock_import` 暂存 PENDING 导入明细（items_json），导入后生成记录并自动选中；确认入库先 CAS `PENDING→CONFIRMED` 防并发/重复执行，再以页面编辑后明细逐条执行（失败落 FAILED 流水，不回滚已成功条目）。新增 `POST /api/admin/stock/imports`、`GET /api/admin/stock/imports`、`POST /api/admin/stock/imports/{id}/confirm`；双端维护页新增导入记录卡片（选择并填写回填编辑器/已入库记录不可重复执行/清空填写解除选择）。幂等升级 upgrade_stock_import_v5.sql（直接 DDL）+ init-dm8-sql.sh V5 分级检测。决策：导入仅暂存不改库存 / 确认以页面编辑明细为准 / CAS 抢占防重复。 | User request: 导入excel之后需要有一条导入记录，用户选择这条记录之后确认操作执行写入商品库存 |
| 2026-09-05 | implemented | 导入流程改造为「后端文件 + 真电子表格编辑」：导入 Excel（base64）存后端目录 `stock.import.dir`（默认 `./upload/stock-import/{id}.xlsx`，StockImportFileStore）；点选记录后前端 JS（SheetJS + Luckysheet 2.1.13，双端本地托管静态资源）打开该 Excel 编辑，保存修改经 `PUT /imports/{id}/file` 覆盖后端文件并同步刷新明细快照（仅 PENDING，状态守卫+CAS）；确认入库改无请求体，`POST /imports/{id}/confirm` 按记录已保存明细执行；新增 `GET /imports/{id}/file`。下载模板/导入 Excel/确认入库三按钮集中到导入记录卡片，行点选记录。移除页面上原「全商品表格逐行填调整量」手工编辑器（`POST /stock/batch` API 保留不变）。决策：文件与明细双写由保存接口保证一致 / 确认以记录已保存明细为准 / Luckysheet 而非轻量表格（用户选择真电子表格组件）/ 手工批量入口移除（用户确认）。 | User request: 前端使用js打开这个excel可以在excel里面进行编辑并保存，excel文件先存在后端目录，点选记录后确认入库才执行写入，三个按钮放到导入记录卡片 |
| 2026-09-05 | implemented | 导入记录点选交互优化（双端对齐）：记录表新增 radio 单选列（仅 PENDING 可选、已入库行禁用），行点选与 radio 同步同一选中状态并高亮当前行；新增行展开明细预览（商品ID/商品名称/调整量子表，正绿负红，空明细提示），React antd `expandable` / Vue Element `type="expand"` 同契约；确认入库按钮左侧实时显示 `已选：#id 文件名（N 条）`/`未选择记录`；未点选直接确认提示 `请先点选一条导入记录`。纯前端交互改造，后端 API 无变化。 | User request: 导入那个excel文件记录我需要进行点选，对点选的文档进行确认入库，请优化功能，并实现react与vue的双端的同步改造对齐 |
| 2026-09-05 | implemented | Excel 编辑器改为新开页签全屏编辑：点行首 ＋ 图标或操作列 `选择并编辑`/`编辑 Excel` 均新开浏览器页签打开 `/#/admin/stock-edit/{id}`（React `StockExcelEditor.jsx` / Vue `StockExcelEditor.vue` + RequireAdmin 路由守卫），该路由隐藏公共头/底、Luckysheet 铺满整页，顶栏 返回（关闭页签）/保存修改，非 PENDING 记录只读核对；修复点 ＋ 白屏（React `itemCols` 引用丢失致 expandedRowRender 抛 ReferenceError 整树卸载）。原 960px Luckysheet 弹窗与行内展开明细预览移除；库存维护页 window focus 自动刷新导入记录。双端构建通过；后端 API 无变化。 | User request: 点击导入记录前面的加号页面空白，需要处理成打开一个新的页签，铺满这个页面 |
