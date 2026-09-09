---
name: "mall-lock-governance-audit"
description: "Scan mall backend for leftover SELECT FOR UPDATE / pessimistic DB locks vs Redis-lock+CAS model. Invoke when auditing FOR UPDATE removal or user asks to check ForUpdate residue."
---

# 商城后端锁语义残留专项核查（Redis 锁 + CAS 治理审计）

本工作区后端（`backend/`，Spring Boot + MyBatis(-Plus) + DM8 + Redisson）状态推进并发模型为
「Redis 分布式锁 + CAS 条件更新」双保险。`AGENTS.md` 领域不变量「状态推进」明确**禁止
`select ... for update`**（autocommit 下行锁空转、远程渠道调用期间空持锁），并发权威由
同维度 Redis 锁 + CAS 兜底。本 skill 是只读审计流程，用于确认后端无 DB 悲观锁残留。

## 触发场景

- 移除/重构 FOR UPDATE 后的复核
- 订单/支付/退款状态机并发模型变更后的核查
- 用户要求“检查有没有遗漏的 ForUpdate / for update”

## 核查清单（5 项，全部必做）

所有扫描根目录均为 `d:\release\mall\backend\src\main`（测试代码 `src/test` 的 mock 引用另查）。

### 1. 跨行/夹注释的 `for update` SQL（multiline 正则）

普通单行 grep 会漏掉 `for // 注释` 换行 `update`、`for /* */ update` 等写法：

- Grep：pattern `(?is)for\s+(?://[^\n]*|/\*.*?\*/|\s)+update`，glob `**/*.{java,xml}`，
  multiline=true，path `backend/src/main`

### 2. MyBatis 注解 SQL（`@Select/@Update/@Insert/@Delete`）

- Grep：pattern `@Select|@Update|@Insert|@Delete`，glob `**/*.java`，path `backend/src/main`
- **误命中排除**：Spring MVC 的 `@DeleteMapping`/`@PostMapping` 等也会命中，必须逐个 Read
  确认命中行属于 MyBatis Mapper 接口而非 Controller。

### 3. 变体方法名

- Grep：pattern `(?i)ForUpdate|forUpdate|findLocked|lockBy|selectLocked|readForLock|withLock|pessimistic`，
  glob `**/*.{java,xml}`，path `backend/src/main`

### 4. 全部 XML mapper 全量扫

- Grep：pattern `(?is)\bfor\b[\s\S]{0,80}?\bupdate\b`，glob `**/*.xml`，multiline=true，
  path `backend/src/main/resources`

### 5. 区分 Redis 锁 vs DB 锁（关键，不可省）

命中 `*WithLock*` / `*LockAnd*` / `executeWithLock*` 命名的方法时，**必须 Read 实现**确认其
锁原语：

- 合规：`DistributedLockTemplate`、`RedissonClient`、`StringRedisTemplate.setIfAbsent`、
  `RedisLockRegistry`
- 违规：`select ... for update`、`EntityManager.lock(..., LockModeType.PESSIMISTIC_*)`、
  JDBC `setLockMode`

参考锚点：`WxPayNotifyHandler.processWithLock` 用 `StringRedisTemplate`（Redis 锁，合规）；
`RefundOrderServiceImpl.executeWithLockAndChannelRefund` 用 `DistributedLockTemplate`（合规）。

## 判定规则

| 命中物 | 归类 | 处理 |
|---|---|---|
| XML `<select>/<update>` 标签体内、`@Select` 注解内的可执行 `for update` SQL | 违规残留 | 列入修复清单 |
| Java 方法定义/调用 `xxxForUpdate(...)` | 违规残留 | 列入修复清单 |
| 其他 DB 悲观锁变体（`lock in share mode` / `updlock` / `rowlock` / `with(nolock)` / `PESSIMISTIC_WRITE`） | 违规残留 | 列入修复清单 |
| 注释/Javadoc/文档中的 “for update” 字样（如“禁止使用 for update”“autocommit 下空转”） | 合规 | 不得改动（属决策说明） |
| `WithLock` 命名但实现为 Redis 锁 | 合规 | 不处理 |

前端扫描注意：`admin-ui/public/luckysheet/` 等**第三方压缩库**会误命中，排除 `public/`、
`node_modules/` 等非自有代码目录。

## 发现残留时的修复指引（按设计变更处理）

删除 FOR UPDATE 不是单纯删 SQL，按 `AGENTS.md` 属设计变更：

1. **先验证每个 ForUpdate 调用点都有「同维度 Redis 锁 + CAS」双保险**。凡发现仅
   `@Transactional` 无 Redis 锁的调用点（历史上 V1 链路 `createRefundApplication`、
   `refreshOrderRefundStatus` 曾如此），**必须先补 Redis 锁再删行锁**，否则并发漏洞。
2. 补锁模式参照 `RefundApplicationServiceImpl.approve`：外层
   `@Transactional(propagation = NOT_SUPPORTED)` + `distributedLockTemplate.execute(lockKey, 5000L, -1L, ...)`
   + 锁内 `transactionTemplate.execute(...)`；锁键按业务维度命名（如
   `payment:refund:create:{orderNo}`、`payment:ali:check:order:{orderNo}`）。
3. 删除顺序：端口接口方法 → 实现类方法 → Mapper 接口方法 → XML SQL → 调用点改普通读
   （`findByXxx`）→ 测试 mock 同步。
4. 同步文档：`AGENTS.md` 领域不变量、`CODE_INTRO.md` 并发机制章节（机制表/流程图节点/
   退款联动表格）、`MyBatisPlusConfig` 乐观锁插件 Javadoc。
5. 门禁：`mvn test`（当前基线 121 tests 全绿）；提交信息不含 agent/厂商署名。

## 完成定义

- [ ] 5 项扫描全部执行并保留命中清单
- [ ] 每个命中点已 Read 确认归类（注释 / 可执行 SQL / 方法定义 / Redis 锁实现）
- [ ] 违规残留为 0；若有，已按“修复指引”列出待修复清单（不夹带无关改动）
- [ ] 审计本身为只读；结论汇报中明确区分“注释字样”与“可执行残留”
