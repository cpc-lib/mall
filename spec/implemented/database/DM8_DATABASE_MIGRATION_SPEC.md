# DM8 Database Migration Spec

## 0. Metadata

- Status: implemented
- Domain: database
- Updated: 2026-06-14
- Owner: TBD
- Related work: Replace MySQL 8 runtime database with Dameng DM8 for `payment-demo-v5`
- Issue classification: Design change + compatibility impact
- Impact scope: backend datasource, Maven JDBC dependency, mapper SQL dialect, database initialization SQL, Docker Compose, database operation docs, tests

## 1. Background

The backend previously used MySQL-oriented runtime configuration, Maven dependency, DDL, and selected SQL fragments. The project now needs to run on Dameng DM8 while preserving the existing payment domain behavior: products, orders, payment flows, refund flows, payment channels/apps, unique constraints, pessimistic row locks, and idempotency guardrails.

This migration is not a business feature change. It changes the database provider and SQL dialect while keeping the Java service API, controller routes, Redis keys, RabbitMQ exchanges/queues/routing keys, payment provider callbacks, and frontend API contracts unchanged.

## 2. Contract

### 2.1 Runtime datasource

- Spring datasource uses `dm.jdbc.driver.DmDriver`.
- Default URL is `jdbc:dm://192.168.1.200:5236?schema=SYSDBA&connectTimeout=30000` through environment placeholders.
- Default username is `SYSDBA`.
- Default password is `Cpc2026#@Dm` because DM8 initialization rejects passwords shorter than 9 characters.
- The project no longer depends on `mysql-connector-java`; it depends on `com.dameng:DmJdbcDriver18`.

### 2.2 SQL dialect

- MySQL-only `limit 1` is removed from mapper XML and MyBatis-Plus wrapper `.last(...)` usage.
- MySQL-only `ifnull(...)` is replaced with `coalesce(...)`.
- Latest unpaid-order locking uses a DM-compatible `rownum <= 1` subquery while preserving `for update` row lock behavior.
- DM8 DDL uses `IDENTITY(1, 1)`, `CLOB`, standard constraints, indexes, comments, and update-time triggers instead of MySQL `AUTO_INCREMENT`, `json`, table engines, character-set/collation clauses, `DROP TABLE IF EXISTS`, and `ON UPDATE CURRENT_TIMESTAMP`.

### 2.3 Docker and SQL initialization

- `payment-demo-v5/env/docker-compose.dm8.yml` starts DM8 on port `5236` and mounts `env/sql/dm8` to `/dm8-init`.
- `payment-demo-v5/env/scripts/dm8/start-dm8.sh` starts the compose stack.
- `payment-demo-v5/env/scripts/dm8/stop-dm8.sh` stops the compose stack while preserving the data volume.
- `payment-demo-v5/env/scripts/dm8/init-dm8-sql.sh` executes all mounted `*.sql` files by file name order through container-local `disql`.
- DM8 initialization must be idempotent without requiring MySQL compatibility mode. Existing tables are dropped through DM/Oracle-style `USER_TABLES` checks before recreation.
- After SQL execution, `init-dm8-sql.sh` verifies that the required runtime tables exist in the current login schema before reporting success.
- `init-dm8-sql.sh --if-missing` first checks the six required runtime tables and skips the destructive rebuild entirely when they are all present, so it never touches a schema that already holds business data.
- `start-dm8.sh` runs `init-dm8-sql.sh --if-missing` after dmserver becomes ready and waits for DM8 to accept logins before returning, so a recreated `dm8-data` volume self-heals its schema during stack startup and the backend is only started against an initialized database.
- Backend startup must not fail solely because payment config tables are missing or unavailable during `PaymentConfigLoader` preloading. Startup falls back to an empty DB payment-config cache and logs one concise warning with the root cause message, without printing the full exception stack; full schema creation remains the responsibility of the DM8 initialization SQL.

## 3. Acceptance Criteria

- [x] `pom.xml` contains the DM8 JDBC dependency and does not contain the MySQL JDBC dependency.
- [x] `application.yml` points to `jdbc:dm://` using `dm.jdbc.driver.DmDriver` and defaults `DM_HOST` to `192.168.1.200`.
- [x] Mapper SQL and service query wrappers do not use MySQL `limit 1` or `ifnull(...)`.
- [x] DM8 initialization SQL exists under `env/sql/dm8/` and avoids MySQL DDL tokens such as `AUTO_INCREMENT`, `ENGINE=InnoDB`, `utf8mb4`, `json NULL`, `DROP TABLE IF EXISTS`, and `ON UPDATE CURRENT_TIMESTAMP`.
- [x] DM8 Docker Compose and shell scripts exist for startup, shutdown, and SQL initialization.
- [x] DM8 initialization script verifies the runtime tables `t_payment_channel`, `t_payment_app`, `t_order_info`, `t_payment_info`, `t_product`, and `t_refund_info` after execution.
- [x] `PaymentConfigLoader` startup preload handles payment config table failure without aborting Spring bean creation.
- [x] Tests assert the migration contract without connecting to real DM8, Redis, RabbitMQ, WeChat Pay, or Alipay.
- [ ] Full Maven test execution passes in an environment with Maven and dependency access.
- [ ] Optional manual DM8 integration verification is run against a real DM8 container.

## 4. Implementation Anchors

| Area | Anchor |
|---|---|
| Maven dependency | `payment-demo-v5/pom.xml` |
| Spring datasource | `payment-demo-v5/src/main/resources/application.yml` |
| Order mapper SQL | `payment-demo-v5/src/main/resources/mapper/OrderInfoMapper.xml` |
| Product mapper SQL | `payment-demo-v5/src/main/resources/mapper/ProductMapper.xml` |
| Refund mapper SQL | `payment-demo-v5/src/main/resources/mapper/RefundInfoMapper.xml` |
| Service wrapper query cleanup | `payment-demo-v5/src/main/java/cc/ivera/service/impl/OrderInfoServiceImpl.java`, `PaymentAppServiceImpl.java`, `PaymentChannelServiceImpl.java` |
| Payment config startup preload tolerance | `payment-demo-v5/src/main/java/cc/ivera/config/PaymentConfigLoader.java` |
| DM8 compose | `payment-demo-v5/env/docker-compose.dm8.yml` |
| DM8 SQL | `payment-demo-v5/env/sql/dm8/001_payment_demo_dm8.sql` |
| DM8 scripts | `payment-demo-v5/env/scripts/dm8/*.sh` |
| DM8 docs | `payment-demo-v5/docs/DAMENG_DM8_OPERATIONS.md`, `payment-demo-v5/README.md` |
| Migration contract test | `payment-demo-v5/src/test/java/cc/ivera/database/Dm8MigrationContractTest.java` |

## 5. Compatibility Impact

Changed intentionally:

- Database provider changes from MySQL-oriented runtime defaults to DM8 runtime defaults.
- SQL initialization file for the default runtime is now DM8-specific.
- MySQL-specific query fragments are removed.
- Operators should use `env/docker-compose.dm8.yml` and `env/scripts/dm8/init-dm8-sql.sh` for local database startup and initialization.
- Initialization now fails fast if required runtime tables are missing after `disql start`, instead of allowing the backend to fail later during `PaymentConfigLoader` startup.
- The backend no longer aborts Spring startup when payment config tables are unavailable during payment config preload. A warning is logged and the runtime DB config cache starts empty.

Unchanged:

- Public HTTP routes, request bodies, response wrapper shape, response text, and frontend API contracts.
- Redis key semantics and Redisson lock usage.
- RabbitMQ exchange, queue, routing-key names, and delayed message semantics.
- Payment provider config sources and callback entry points.
- Business tables and core unique constraints used for idempotency.
- Explicit payment config reload and config CRUD behavior: they still access the DB-backed config tables and surface schema/data problems.

Migration and rollback notes:

- Re-run `bash env/scripts/dm8/init-dm8-sql.sh` against the same DM8 instance that the backend connects to, with `DM_USERNAME`, `DM_PASSWORD`, and `DM_SCHEMA` aligned to `application.yml`.
- The bundled `001_payment_demo_dm8.sql` is a destructive full initialization script. Back up data or use a targeted migration before running it against a database that must preserve orders, payments, refunds, or payment config.
- To roll back this initialization hardening only, restore the previous SQL/script pair and rerun initialization in a disposable database; public HTTP API, frontend API, Redis keys, RabbitMQ names, and provider callbacks are unchanged.

## 6. Verification

Static verification performed in this workspace:

```bash
grep -RIn --exclude-dir=target -E 'limit 1|ifnull\(|jdbc:mysql|com\.mysql|mysql-connector-java|AUTO_INCREMENT|ENGINE = InnoDB|utf8mb4|ON UPDATE CURRENT_TIMESTAMP|json NULL|`t_' payment-demo-v5/src/main payment-demo-v5/pom.xml payment-demo-v5/env/docker-compose.dm8.yml payment-demo-v5/env/scripts/dm8 payment-demo-v5/env/sql/dm8
```

Result on 2026-06-13: no matches.

Maven verification command to run in a development machine:

```bash
cd payment-demo-v5
mvn "-Dtest=Dm8MigrationContractTest,PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test
```

Result on 2026-06-14 after rollback in this workspace: 25 tests passed, 0 failures, 0 errors.

Manual DM8 verification command:

```bash
cd payment-demo-v5
bash env/scripts/dm8/start-dm8.sh
bash env/scripts/dm8/init-dm8-sql.sh
mvn spring-boot:run
```

## 7. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-06-14 | withdrawn | Reverted application startup runtime-table bootstrap; full runtime schema creation remains in the DM8 initialization SQL. | User requested rollback |
| 2026-06-14 | withdrawn | Reverted non-destructive startup bootstrap for missing DB-backed payment config tables/default rows. | User requested rollback |
| 2026-06-14 | implemented | Made `PaymentConfigLoader` startup tolerant of missing DB-backed payment config tables while preserving explicit DB reload/CRUD behavior. | Startup failure: `t_payment_channel` missing |
| 2026-06-14 | implemented | Hardened DM8 initialization so required runtime tables are created without MySQL-compatible `DROP TABLE IF EXISTS` and verified before success. | Startup failure: `t_payment_channel` missing |
| 2026-06-13 | implemented | Replaced default database stack and SQL dialect from MySQL-oriented configuration to DM8. | User request |
| 2026-09-05 | implemented | Added `--if-missing` guard to `init-dm8-sql.sh` and auto schema validation/self-heal to `start-dm8.sh`; enforced LF endings for `*.sh`/`*.sql` via `.gitattributes`. | Recurrence of ISSUE-DB-002: backend started against a recreated DM8 volume without initialization |
