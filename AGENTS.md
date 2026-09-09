# 电商商城平台 Agent Rules

本文件是编码 Agent 的项目级规则手册。保持改动小步、有记录、可验证。

仓库布局：

- `backend/`：Spring Boot 2.3.7（Java 8，包名 `cc.ivera`）单体后端，数据库达梦 DM8，缓存/锁 Redis + Redisson，消息队列
  RabbitMQ，支付渠道微信 V2/V3 + 支付宝。代码按 DDD 限界上下文划分为 8 个包：`shared`（共享内核）、`product`（商品与库存）、
  `order`（订单与履约）、`payment`（支付）、`refund`（退款）、`user`（用户/收货地址/行政区划）、`cart`（购物车）、`bill`（账单对账）；
  每个上下文内部统一四层：`interfaces`（Controller/DTO/VO/MQ 消费者/定时任务）、`application`（应用服务接口与 impl）、
  `domain`（纯 POJO 领域模型、枚举、策略、仓储与能力端口）、`infrastructure`（PO/Mapper/Converter/RepositoryImpl 位于
  `infrastructure.persistence`，另有网关实现、MQ 拓扑、存储适配、配置）。
- `user-ui/`：React 18 用户商城（Vite 5 + antd 5，HashRouter，dev 端口 3000，移动 App 风格）。
- `admin-ui/`：React 18 管理后台（Vite 5 + antd 5 + SheetJS + Luckysheet，HashRouter，dev 端口 3002）。
- 两个前端通过 CORS 直连后端 `http://localhost:8080`，无前端代理。

文档账本：`README.md`（面向使用者的功能与启动说明）、`CODE_INTRO.md`（面向开发者的架构/实体/路由/幂等设计导览）、
`backend/docs/`（DM8 与 RabbitMQ 运维手册）。

## 问题分类

实施前必须先给问题定性：

| 类别             | 判定条件                                                              | 必须动作                                                                                                                  |
|----------------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| 局部缺陷           | 既有契约清晰，仅单一行为出错。                                                   | 补充或更新针对性测试，做最小修复，跑受影响测试与全量回归。                                                                                         |
| 设计变更           | 改动影响工作流、状态规则、领域模型、架构边界、配置来源或渠道对接行为。                               | 动代码前在提交/PR 说明中写清「现状 → 目标方案 → 影响面」，并同步更新 CODE_INTRO.md 对应章节。                                                           |
| 公共 API / 兼容性影响 | 任何路由、请求/响应结构、响应文案、状态值、DB schema、事件名、配置键、前端调用、渠道回调、日志/审计契约或遗留路径变化。 | 兼容性影响与迁移/回滚说明写入提交/PR 描述；DB 变更只改 `backend/env/sql/dm8/schema.sql` 一体化全量脚本（可重复执行、含遗留表 DROP 守卫，全新库与存量库执行后结构一致），不再维护增量脚本。 |
| 多问题同根因         | 多个报告指向同一条共享规则、抽象、配置源、幂等规则或状态流转。                                   | 先明确共享规则再统一修改，禁止逐个打补丁。                                                                                                 |

分类不明确时，按设计变更处理，先补齐方案说明再动代码。

## 分支命名

只使用以下前缀：

- `bug-fix/<issue-or-topic>`：局部缺陷修复。
- `feature/<issue-or-topic>`：新行为或设计变更。
- `refactor/<topic>`：行为保持不变的重构。
- `update/<topic>`：文档、配置或治理类变更。

分支、PR 与提交名称不得包含 Agent 名称、厂商名称、作者签名或任何生成标记。

## 领域不变量（改动不得破坏）

- **库存四桶恒等式**：`available_stock + locked_stock + sold_stock + lost_stock` 恒等于入库总量；任何库存变动必须写
  `t_inventory_transaction` 流水，并以 `biz_no`（`TYPE:单号:明细id`）做幂等锚（预检 selectCount + 唯一键兜底）。
- **防超卖根闸门**：`ProductMapper.reserveStock` 条件 UPDATE（`WHERE available_stock >= qty`，product 上下文
  infrastructure 层），下单库存不足必须整单回滚，不允许「先下单后补扣」。
- **支付成交唯一性**：本地订单 1:N 渠道支付尝试（`t_payment_order`），仅允许一笔 SUCCESS 成交；重复/晚到支付由
  `PaymentSuccessService` 自动原路退款冲正，冲正不动库存。
- **退款防超退**：受理即冻结可退额度，金额以服务端 `refund.domain.policy.RefundPolicy` 为准；仅退款不退货必须核销货损（未收货
  `locked→lost`、已收货 `sold→lost`），货物不回仓，不走回补。
- **状态推进**：订单/支付/退款单状态流转一律走「Redis 分布式锁 + CAS 条件更新」双保险，禁止先改后查；同维度（订单号/退款单号）操作由调用方在事务外加锁串行化，事务内仅做读 + CAS 写。**禁止使用 `select ... for update`**（当前代码中已无任何 for update 语句：autocommit 下行锁空转、远程调用期间空持锁，DM8 读已提交隔离级下无匹配行时也不产生间隙锁），并发权威由 Redis 锁 + CAS 兜底。
- **跨上下文依赖方向**：跨上下文只允许依赖对方 `domain`（领域模型、repository/gateway 端口）或 application 服务接口，禁止引用对方
  `infrastructure`（PO / Mapper / RepositoryImpl）。

## 测试要求

- 后端测试套件位于 `backend/src/test`（当前 16 个测试类、121 个用例）：纯 JUnit 5 + Mockito，覆盖领域聚合状态机与守卫、PO
  Converter 全字段往返、`RefundPolicy` 额度核算、`WxTradeBillParser` 账单解析、登录防护、支付成功/退款/购物车应用服务；
  **不连真实 DM8、Redis、RabbitMQ 或支付渠道**。
- 新行为或缺陷修复必须补针对性测试；重构既有行为前先补/对齐特征测试锁定现状。
- 特征测试不得评判现状是否合理；可疑现状须在测试名或注释中标注 `现状`。
- 触碰 DB、Redis、缓存、全局状态、MQ、配置或时钟的测试必须重置状态并使用临时隔离（桩件/Mockito，不启真实容器）。
- 前端逻辑测试使用 Node 内置 test runner（仅 user-ui）：
    - `user-ui`：`npm run test:logic`（Token 单飞刷新）；admin-ui 无前端测试。
- 行为保持类改动，需在 `backend/` 跑 `mvn test`，并跑 user-ui 的 `test:logic`，确认无回归。
- 重构后若测试失败，先说明锁定的是哪条行为、为何变化，再做最小修正。

## 文档同步规则

- `CODE_INTRO.md` 是架构与公共行为的说明账本：幂等规则、状态机、库存/退款/支付联动、MQ 拓扑、前端页面能力、上下文边界等发生变化时，必须同步对应章节，包括其中的架构图与表格。
- `README.md` 面向使用者：新功能、启动方式、依赖、目录结构变化时必须同步。
- DB schema 只有一个一体化脚本 `backend/env/sql/dm8/schema.sql`（可重复执行，含遗留表 DROP 守卫，等同清库重建）：任何 schema
  变更只改该文件，保证全新库与存量库执行后结构一致。
- 实现、测试、文档三者不一致，视为工作未完成。

## 完成定义（DoD）

一项改动只有满足以下所有适用项才算完成：

- 问题定性明确，或可从 PR/提交说明直接看出。
- 公共 API 与兼容性影响无变化，或已在提交/PR 说明中记录（含迁移/回滚方式）。
- 领域不变量与跨上下文依赖方向未被破坏。
- 新增/受影响测试与全量回归全部通过（`mvn test`、user-ui `test:logic`）。
- `CODE_INTRO.md` / `README.md` 与实现保持一致；DB 变更落在 `schema.sql`。
- 未夹带任何无关的业务行为、缺陷修复或功能。
