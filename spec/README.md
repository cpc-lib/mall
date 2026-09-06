# Spec State Ledger

> Updated: 2026-06-12

`spec/` is the project state ledger for behavior, contracts, architecture boundaries, and governance. It is intentionally small: every entry should help future changes answer "what is the current contract, where is it implemented, and how do we prove it?"

## States

| State | Directory | Meaning |
|---|---|---|
| governance | `spec/governance/` | Always-on process rules, issue classification, PR checklists, and spec accounting rules. |
| planned | `spec/planned/` | Designed but not fully landed behavior, compatibility changes, or refactors that still need implementation/tests. |
| implemented | `spec/implemented/` | Current behavior that is already landed and linked to implementation/test anchors. |
| archived | `spec/archived/` | Deferred, deprecated, or abandoned decisions kept for history. |

## Current Ledger

| Spec | State | Purpose |
|---|---|---|
| `spec/governance/ISSUE_LEDGER.md` | governance | Current issue classification for the discovered legacy behavior. |
| `spec/governance/PR_SPEC_RECONCILIATION_CHECKLIST.md` | governance | Checklist to run before PR merge. |
| `spec/implemented/current-behavior/PAYMENT_DEMO_CURRENT_BEHAVIOR_SPEC.md` | implemented | Current behavior contract for public API, state effects, events, logs, and characterization tests. |
| `spec/implemented/frontend/REACT_LOGO_BRANDING_UPDATE_SPEC.md` | implemented | React header logo brand text update. |
| `spec/implemented/frontend/VUE_LOGO_BRANDING_UPDATE_SPEC.md` | implemented | Vue header logo brand text update. |
| `spec/implemented/frontend/VUE_HEADER_LOGOUT_SHORTCUT_SPEC.md` | implemented | Vue header quick logout entry for logged-in users. |
| `spec/implemented/current-behavior/ADMIN_ORDERING_RESTRICTION_AND_STOCK_MANAGEMENT_SPEC.md` | implemented | Admin ordering restriction, admin product stock management API/UI, top-right logout for React and Vue. |
| `spec/implemented/database/DM8_DATABASE_MIGRATION_SPEC.md` | implemented | Default backend database provider migrated from MySQL-oriented runtime to Dameng DM8. |
| `spec/implemented/trading/TRADING_MODEL_V2_SPEC.md` | implemented | 交易系统六域模型改造：下单预占/支付提交/退款冻结结转/发货物流收货状态机、四维状态列、超时关单渠道无单本地关单、React/Vue V2 前端（含微信扫码二维码弹窗）。 |
| `spec/implemented/admin/ADMIN_ORDER_EXCEPTION_HANDLING_SPEC.md` | implemented | 管理员全部订单管理 + 强制关单 + 标记支付 + 退款失败原因可见性（用户/管理员）。 |
| `spec/implemented/api/UNIFIED_ERROR_CODE_SPEC.md` | implemented | 统一错误码契约：0成功/-1业务/400参数/401未认证/403无权限/404资源不存在/500系统异常；错误响应统一经 GlobalExceptionHandler 与 AuthInterceptor 输出；4 个前端拦截器判定改为 code!==0。 |

## Directory Layout

```text
spec/
|-- README.md
|-- governance/
|   |-- ISSUE_LEDGER.md
|   `-- PR_SPEC_RECONCILIATION_CHECKLIST.md
|-- planned/
|-- implemented/
|   |-- current-behavior/
|   |   `-- PAYMENT_DEMO_CURRENT_BEHAVIOR_SPEC.md
|   |-- database/
|   |   `-- DM8_DATABASE_MIGRATION_SPEC.md
|   |-- frontend/
|   |   |-- REACT_LOGO_BRANDING_UPDATE_SPEC.md
|   |   `-- VUE_LOGO_BRANDING_UPDATE_SPEC.md
|   `-- trading/
|       `-- TRADING_MODEL_V2_SPEC.md
|   `-- admin/
|       |-- ADMIN_ORDER_EXCEPTION_HANDLING_SPEC.md
|       `-- ADMIN_USER_MANAGEMENT_LOGIN_PROTECTION_SPEC.md
`-- archived/
    |-- deferred/
    `-- deprecated/
```

## Operating Rule

Before code changes, classify the issue and find or create the matching spec. After code changes, reconcile the spec state, implementation anchors, compatibility impact, and tests in the same PR.

