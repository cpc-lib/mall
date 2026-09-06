# PR Spec Reconciliation Checklist

> Updated: 2026-06-09

Use this checklist before merging any PR that touches behavior, tests, docs, examples, configuration, public API, provider callbacks, events, or state.

## 1. Issue And Scope

- [ ] The issue is classified as local bug, design change, public API/compatibility impact, or multiple issues with the same root cause.
- [ ] The branch prefix matches the issue class: `bug-fix/`, `feature/`, `refactor/`, or `update/`.
- [ ] The PR does not bundle unrelated bug fixes, features, refactors, or docs churn.

## 2. Spec State

- [ ] A related spec exists under `spec/governance/`, `spec/planned/`, `spec/implemented/`, or `spec/archived/`.
- [ ] New or changed behavior starts in `spec/planned/<domain>/`.
- [ ] Landed behavior has implementation anchors and, if complete, is under `spec/implemented/<domain>/`.
- [ ] Deferred or deprecated decisions are moved to `spec/archived/` with reason and date.
- [ ] `spec/README.md` is updated when the ledger gains, moves, or archives a spec.

## 3. Contract And Compatibility

- [ ] HTTP routes, request shapes, response structures, response text, status values, and validation behavior are unchanged or explicitly documented.
- [ ] DB tables/fields, Redis keys, RabbitMQ exchange/routing key/queue names, config keys, and provider callback formats are unchanged or explicitly documented.
- [ ] React and Vue frontend compatibility has been considered when backend API behavior changes.
- [ ] Legacy routes are preserved or a planned spec documents deprecation and migration.
- [ ] Logs/audit records that tests or operators rely on are unchanged or explicitly documented.

## 4. Tests

- [ ] Characterization tests exist for current legacy behavior before refactoring.
- [ ] Suspicious current behavior remains marked with `现状`.
- [ ] Tests reset or isolate DB, Redis, cache, globals, config, MQ, and clock state when they touch those surfaces.
- [ ] The characterization suite passes:

```powershell
mvn "-Dtest=PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test
```

- [ ] Any additional impacted unit/integration/frontend tests pass or are documented as not applicable.

## 5. Final Review Statement

Before merge, the PR description should state:

- Issue classification.
- Specs changed and their final state.
- Tests run and result.
- Compatibility impact: none, documented, or intentionally changed by spec.
- Any remaining planned or archived follow-up.

