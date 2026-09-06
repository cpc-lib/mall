# 统一错误码规范（Unified Error Code）

> State: planned
> Updated: 2026-09-06
> Issue class: Design change + Public API compatibility impact（错误响应 `code` 字段取值变更）
> Status: 设计已确认，实现进行中

## 1. 背景与问题

改造前错误响应分散在三处，且错误码不区分：

| 位置 | 改造前行为 | 问题 |
|---|---|---|
| `GlobalExceptionHandler` | 业务异常 / 参数校验 / 系统异常全部返回 `code=-1`（HTTP 200） | 所有错误无区分 |
| `AuthInterceptor.reject` | 手写 LinkedHashMap 返回 `code=-401/-403`（HTTP 401/403） | 绕过 `R` 结构，风格不统一 |
| `PaymentChannelController.getById` / `PaymentAppController.getById` | 手工 `R.error("xxx不存在")`，`code=-1` | 绕过统一异常处理入口 |

## 2. 统一错误码契约

| code | 含义 | 产生来源 |
|---|---|---|
| 0 | 成功 | `R.ok(...)` |
| -1 | 业务错误 | `BizException`（默认码，全库 341 处调用点零改动） |
| 400 | 参数校验失败 | `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` |
| 401 | 未认证 | `AuthInterceptor`（HTTP 401，body code 由 -401 改为 401） |
| 403 | 无权限 | `AuthInterceptor`（HTTP 403，body code 由 -403 改为 403） |
| 404 | 资源不存在 | 控制器资源缺失场景（原手工 `R.error`，改抛 `BizException(NOT_FOUND)`） |
| 500 | 系统异常 | `GlobalExceptionHandler` 兜底 `Exception`（原 code=-1） |

错误响应统一结构（HTTP 200，除 AuthInterceptor 维持 HTTP 401/403 外）：

```json
{ "code": 400, "message": "xxx不能为空", "data": null }
```

## 3. 兼容性影响与迁移

### 3.1 前端（必须同步）

4 个前端拦截器以 `res.code < 0` 判定错误（vue / vue-admin / react / react-admin 的 `src/utils/request.js`）。
正数码 400/404/500 会被 `code < 0` 误判为成功 → **必须同步改为 `res.code !== 0`**。

- 刷新令牌单飞逻辑已用 `payload.code !== 0`，无需改动。
- HTTP 401 触发刷新重试的逻辑依赖 HTTP 状态码，AuthInterceptor 的 HTTP 401/403 行为保持不变，不受影响。
- 前端无任何按具体错误码值（-1/-401/-403）分支的逻辑（已全量检索确认）。
- 错误响应 `data` 统一为 `null`（改造前业务异常路径为 `{}`、其余为 `null` 的不一致一并消除）；前端错误分支只读 `code`/`message`，无影响。

### 3.2 后端行为保持不变

- `BizException(String)` / `BizException(String, Throwable)` 构造器保留，默认码 -1，341 处 `throw new BizException(...)` 调用点零改动。
- 新增 `BizException(ErrorCode[, message[, cause]])` 重载，按需标注专属错误码。
- 业务/校验/系统错误仍返回 HTTP 200 + 负/正数 code；仅 AuthInterceptor 维持 HTTP 401/403。
- 微信/支付宝支付通知（`/notify`）为渠道协议响应，不走 `R` 契约，不受影响。

### 3.3 回滚

恢复 `GlobalExceptionHandler`、`AuthInterceptor`、`PaymentChannelController`、`PaymentAppController`、`R`、`BizException` 原实现，并将 4 个 `request.js` 判定还原为 `res.code < 0` 即可，无数据迁移。

## 4. 实现锚点

> 2026-09-06 已验证：以下锚点均指向实际落地代码，错误响应构造仅存在于 GlobalExceptionHandler 与 AuthInterceptor 两个统一入口。

| 组件 | 文件 |
|---|---|
| 错误码枚举 | `payment-demo/src/main/java/cc/ivera/exception/ErrorCode.java`（新增） |
| 业务异常携带错误码 | `payment-demo/src/main/java/cc/ivera/exception/BizException.java` |
| 响应封装 | `payment-demo/src/main/java/cc/ivera/vo/R.java`（新增 `error(ErrorCode, String)`；移除改造后孤儿方法 `error()` / `error(String)` / `data(String, Object)`） |
| 全局异常处理 | `payment-demo/src/main/java/cc/ivera/handler/GlobalExceptionHandler.java` |
| 认证拦截 | `payment-demo/src/main/java/cc/ivera/security/AuthInterceptor.java` |
| 资源不存在 | `payment-demo/src/main/java/cc/ivera/controller/PaymentChannelController.java`、`PaymentAppController.java` |
| 前端拦截器 | `payment-demo-vue/src/utils/request.js`、`payment-demo-vue-admin/src/utils/request.js`、`payment-demo-react/src/utils/request.js`、`payment-demo-react-admin/src/utils/request.js` |

## 5. 验收标准

1. `mvn compile` 通过。
2. 所有 Controller 公开接口不再手工构造错误响应（无 `R.error(...)` 散落），错误统一经 `GlobalExceptionHandler` 或 `AuthInterceptor` 输出。
3. 错误响应 `code` 严格取自 `ErrorCode` 枚举，无魔法数字。
4. 4 个前端拦截器错误判定为 `code !== 0`，-1 / 400 / 404 / 500 均能弹出错误提示。
5. HTTP 401 刷新重试链路行为不变。
