# Payment Demo V5 Agent Rules

This file is the project-level rulebook for coding agents. Keep changes small, spec-led, and verifiable.

## Issue Classification

Every issue must be classified before implementation:

| Class | When To Use | Required Action |
|---|---|---|
| Local bug | Existing contract is clear and only one narrow behavior is wrong. | Add or update a focused test, make the smallest fix, run affected tests and characterization tests. |
| Design change | The issue changes a workflow, state rule, domain model, architecture boundary, config source, or provider behavior. | Create or update a `spec/planned/<domain>/...` spec before code changes. |
| Public API or compatibility impact | Any route, request shape, response structure, response text, status value, DB schema, event name, config key, frontend call, provider callback, log/audit contract, or legacy path changes. | Document compatibility impact and migration/rollback notes in the spec before code changes. |
| Multiple issues, same root cause | Several reports point to one shared rule, abstraction, config source, idempotency rule, or state transition. | Group them under one spec and avoid one-off patches until the shared rule is explicit. |

If classification is unclear, treat it as a design change and write the spec first.

## Branch Naming

Use one of these prefixes:

- `bug-fix/<issue-or-topic>` for local bug fixes.
- `feature/<issue-or-topic>` for new behavior or design changes.
- `refactor/<topic>` for behavior-preserving refactors.
- `update/<topic>` for docs, spec, config, or governance-only changes.

Branch, PR, and commit names must not include agent names, vendor names, author signatures, or generated-by markers.

## Test Requirements

- Before refactoring legacy behavior, add characterization tests that lock the current behavior.
- Characterization tests must not judge whether current behavior is reasonable.
- Suspicious current behavior must be marked with `现状` in the test name or comment.
- Tests that touch DB, Redis, cache, global state, MQ, config, or clock state must reset state and use temporary isolation.
- Do not call real payment providers, real Redis, real RabbitMQ, or real MySQL from characterization tests unless an integration test spec explicitly requires it.
- For behavior-preserving work, run:

```powershell
mvn "-Dtest=PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test
```

- If a test fails after a refactor, first explain which locked behavior changed, then make the smallest correction.

## Spec Reconciliation Rules

- Treat `spec/` as the state ledger for public behavior, architecture contracts, compatibility rules, and governance.
- Before changing code, find the related spec. If none exists, create one in `spec/planned/<domain>/`.
- A planned spec moves to `spec/implemented/<domain>/` only after implementation anchors and tests prove it is landed.
- Partially delivered work stays in `planned/` with completed and remaining acceptance criteria marked clearly.
- Deprecated or abandoned decisions move to `spec/archived/`, keeping the reason and date.
- If implementation, tests, docs, and spec disagree, the work is not complete.

## Definition Of Done

A change is done only when all applicable items are true:

- The issue class is documented or obvious from the PR.
- Public API and compatibility impact are unchanged or explicitly documented.
- Related specs are created or updated in the correct state.
- Characterization tests and impacted tests pass.
- Implementation anchors in the spec point to real files/classes/tests.
- No unrelated business behavior, bug fix, or feature is bundled into the change.

