---
name: "mall-ddd-context-migration"
description: "mall backend DDD 限界上下文迁移清单：四层搬移、PO/领域模型拆分、Repository 端口收口、XML namespace 复核、编译与单测门禁。Invoke when migrating a backend bounded context into cc.ivera.<context> DDD layout."
---

# mall backend DDD 限界上下文迁移流程

把 `cc.ivera` 旧平铺包（entity/mapper/service/controller/vo/dto/enums/event/mq/job/config）按限界上下文
迁入 `cc.ivera.<context>.{interfaces,application,domain,infrastructure}`。已完成上下文：shared/product/order/payment/refund/user。
**业务逻辑一行不改（等价搬移），对外 REST API 完全不变（URL/JSON/状态码/文案）。**

## 0. 硬约束（每批不得违反）

- 不新增 Maven 依赖；不自动 git commit；中文交流；最小改动，禁止投机代码/预留占位。
- 领域不变量不得破坏（库存四桶、防超卖 CAS、支付成交唯一、退款防超退、状态行锁+CAS），见 AGENTS.md。
- 包结构：上下文内四层；PO 在 infrastructure（`XxxPO` 后缀，`@TableName` + `extends BaseEntity`，MP 注解保留）；
  纯领域实体在 `domain.model`（**类名与旧实体一致**、`@Data`、自带 `id/createTime/updateTime`）；手写全字段 Converter（null 安全）。
- Repository 按聚合根设端口（`domain.repository`），实现落 `infrastructure.persistence.repository`；
  CAS/行锁 SQL 闸门留在 Mapper/XML；LambdaWrapper 只允许出现在 RepositoryImpl。
- 充血规则等价搬入聚合根/域策略；行为可疑的现状在测试名/注释标 `现状`，不评判。
- 未迁移上下文临时直连新上下文 PO/Mapper 属允许例外（如 bill 未迁时直连 refund PO），记录留后续批次收口。

## 1. 标准流程（9 步，建议直接建 todo）

1. **勘察**：列齐旧文件——entity / mapper(+resources/mapper/*.xml) / enums / service 接口+impl /
   controller / vo / dto / event / mq / job / config / domain(旧域策略)；grep 全库旧包名引用
   （`cc.ivera.entity.Xxx`、`mapper.XxxMapper`、`service.XxxService`、`vo.XxxVO`、`dto.xxx.`），
   区分「待删文件内部互引」与「跨上下文外部引用」。
2. **domain**：建 `domain.model`（字段=旧实体全字段；无审计时间字段的表如 t_region，domain 与 PO 都不带时间字段、
   PO 不继承 BaseEntity）、`domain.enums`、`domain.repository` 端口（把 impl/controller 里的查询意图收口为命名方法，
   如 listByUser/findDefaultByUser/casXxx/listProcessingApproved）。
3. **infrastructure**：PO + Mapper（`@Mapper extends BaseMapper<XxxPO>`，自定义 SQL 方法签名照搬）+
   Converter（toPO/toDomain 全字段、null 判空）+ RepositoryImpl（`@Repository`，Wrapper/方言降级/last("limit 1") 全落这里；
   insert 后回写 id/createTime/updateTime 到 domain；MP 实体更新 NOT_NULL 语义，补丁式更新复刻
   `toPO(patch)` + 键字段置 null + `wrapper.eq(键)`）。
4. **application**：服务接口（**import interfaces.dto/vo**，与 refund/user 批次一致）+ impl；
   领域事件与 AFTER_COMMIT listener 落 `application.event`；构造器循环用 `@Lazy` 打断；
   impl 只依赖 domain.repository 端口与其他上下文 application 接口，禁止出现 mapper/po。
5. **interfaces**：controller（URL/`@RequestMapping`/`R<>`/注解/消息文案一字不动，仅改 import；
   `import ...web.bind.annotation.*` 通配符是仓库统一惯例，保持）、dto/vo 平铺到 `interfaces.dto`/`interfaces.vo`
   （controller 直接返回 domain model 的 JSON 契约不变，字段与 PO 同名）、mq consumer、job
   （job 内联 LambdaQueryWrapper 必须收口为仓储方法）。
6. **跨上下文收口**：按第 1 步 grep 结果，把外部上下文的旧包引用逐个改到新包；
   共享内核（如 `shared.security` 的 AuthContext/AuthPrincipal/JwtTokenService 被多上下文共用）保留原位，仅改其 import；
   跨上下文需要的查询/更新能力补到对应上下文的 domain 端口（如 order 补 casCloseIfFullRefunded、product 收 RefundStockLine 边界类型）。
7. **编译门禁**：`cd backend; mvn -q clean compile -DskipTests`（PowerShell 用 `;` 连接，禁 bash heredoc）。
8. **删旧 + 兜底**：grep 确认旧包名残留**只存在于待删文件内部**后，删旧文件与空目录；
   **必须复核 `backend/src/main/resources/mapper/*.xml` 的 namespace 与 resultType/parameterType 已指向新包**
   （javac 不校验 XML，漏改运行时才炸——已两次踩中）。
9. **单测门禁**：新增 Converter 往返测试（new domain → set 全字段 → toDomain(toPO(x)) 逐字段 assertEquals；
   `assertNull(toPO(null))` / `toDomain((XxxPO)null)`）+ 纯域策略单测（尾差/边界/异常分支）；
   跑 `cd backend; mvn clean test`（**必须带 clean**，否则 IDE JDT 增量编译的 "Unresolved compilation problem" class 污染结果）；
   行为保持类改动再跑 `user-ui` 的 `npm run test:logic`。测试禁连真实 DM8/Redis/RabbitMQ/支付渠道。

## 2. 分层 grep 兜底（删旧后必跑，全部应无匹配）

- `cc.ivera.<context>.domain` 内：`org.springframework` / `com.baomidou` / `infrastructure` → 零匹配。
- `<context>.application` 与 `<context>.interfaces` 内：`persistence.(mapper|po|converter)` 直连 → 零匹配（RepositoryImpl 自身除外）。
- 全库旧包名：`cc.ivera.(entity|mapper|service|vo|dto|event|mq|job|config|enums).<旧名>` → 零匹配（bill 等未迁上下文的临时例外除外）。
- `src/main/resources` 内旧包名字符串 → 零匹配。

## 3. 踩坑固化

- **同文件禁止并行 Edit**：一条消息里对同一文件发多个 Edit 会静默覆盖丢改动。每条消息每个文件最多 1 个 Edit，串行改。
- **XML 三处改全**：namespace、resultType（实体类）、parameterType 引用；自定义 SQL 的方法名/参数与新 Mapper 一致。
- **死代码原则**：旧 Mapper/XML 中无人调用的方法（sumXxx 等）签名照搬保留，不删除、也不新搬入端口；
  impl 中标 `@Deprecated` 的私有死方法原样保留。
- **占位常量零容忍**：迁移后检查 impl 无未使用字段（如 `Collections.emptyList()` 占位）；但被真实签名使用的 import（如 `Collection<String>`）不能误删。
- **编译错误中文乱码（GBK）**：类名/行号/符号名可读，按「找不到符号 + 文件:行号」定位。
- **PowerShell**：禁 tail/find/grep（用 Grep/Glob/Read 工具）；命令连接用 `;`；surefire 汇总读
  `target/surefire-reports/*.txt` 的 `Tests run` 行。
- 旧逻辑取回：`git show HEAD:./src/main/java/cc/ivera/service/impl/XxxServiceImpl.java`。
- 工作区前端改动（admin-ui/user-ui）与后端迁移无关，勿动。

## 4. 完成定义（对齐 AGENTS.md DoD）

问题定性明确；无公共 API/兼容性变化（或已记录迁移/回滚）；领域不变量未破坏；
`mvn clean test` 全绿 + user-ui `test:logic` 全绿；grep 兜底四项零残留；未夹带无关改动。
CODE_INTRO.md 架构章节在全部上下文迁完后（当前计划：批次 7 bill 收口时）统一更新，批次中途不改。
