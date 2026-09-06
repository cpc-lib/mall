# Stock Import File Storage Spec（Excel 导入文件可配置存储：本地 / MinIO）

状态：implemented（2026-09-05；真实 MinIO 连通性需部署环境冒烟验证，见 §6 遗留风险）
日期：2026-09-05
关联：`spec/implemented/current-behavior/ADMIN_ORDERING_RESTRICTION_AND_STOCK_MANAGEMENT_SPEC.md`（V5 导入记录契约）

## 1. 问题与动机

V5 导入记录将 Excel 文件固定保存在后端本地目录（默认 `./upload/stock-import`），目录不可配置，
也不支持对象存储。需要：

1. 存储位置可配置；
2. 除本地磁盘外支持 MinIO 对象存储；
3. 导入记录表（t_stock_import）持久化文件存储后端与地址，读取按记录自身地址路由。

## 2. 问题分类

设计变更（新增配置源 + 存储提供方行为 + DB schema 变更），并含兼容性影响（见 §6）。
按规则先立 spec 再改代码。

## 3. 契约

### 3.1 配置（application.yml，前缀 stock.import）

| 配置键 | 默认 | 说明 |
|---|---|---|
| `stock.import.storage` | `local` | 写入后端：`local` 本地磁盘 / `minio` 对象存储 |
| `stock.import.dir` | `./upload/stock-import` | local 后端保存目录（保留 V5 原键，兼容不变） |
| `stock.import.minio.endpoint` | 无 | storage=minio 时必填，如 `http://192.168.1.200:9000` |
| `stock.import.minio.access-key` | `minioadmin` | MinIO 访问键 |
| `stock.import.minio.secret-key` | `minioadmin` | MinIO 密钥 |
| `stock.import.minio.bucket` | `payment-demo` | 存储桶（不存在时自动创建） |

endpoint/access-key/secret-key/bucket 支持环境变量覆盖：
`STOCK_IMPORT_MINIO_ENDPOINT / _ACCESS_KEY / _SECRET_KEY / _BUCKET`。

### 3.2 存储接口（cc.ivera.storage）

- `StockImportFileStore`（接口，取代原具体类）：
  - `String type()`：返回 `LOCAL` / `MINIO`；
  - `String save(Long id, byte[] bytes)`：写入文件，返回可持久化地址（LOCAL=文件绝对路径，MINIO=对象键）；
  - `byte[] read(Long id, String location)`：按地址读取；`location` 为空回退默认地址
    （LOCAL=`{dir}/{id}.xlsx`，MINIO=`stock-import/{id}.xlsx`），兼容 V5 存量记录；文件缺失返回 `null`。
- `LocalStockImportFileStore`：原本地实现迁入，行为不变。
- `MinioStockImportFileStore`：对象键 `stock-import/{id}.xlsx`；save 前确保 bucket 存在；
  读取遇 NoSuchKey/NoSuchBucket 视为文件缺失返回 null。
- `StockImportFileStorage`（路由门面，Service 唯一依赖）：
  - `String writeType()`：返回配置的写入后端（LOCAL/MINIO），MINIO 未启用时报业务异常；
  - `String save(String storageType, Long id, byte[] bytes)`：按指定后端写入（storageType 空按 LOCAL）；
  - `byte[] read(String storageType, Long id, String location)`：按记录自身后端读取（storageType 空按 LOCAL）；
    指定 MINIO 但未启用时抛业务异常"MinIO 存储未启用，无法访问该导入文件"；未知后端报错。

### 3.3 路由规则

- 新导入（createImport）：写入 `stock.import.storage` 指定的后端，并把 `storage_type` + `file_path`
  回写进记录；
- 编辑保存（saveImportFile）：文件跟随记录原件——按记录自身 `storage_type` 写入原后端，不迁移；
- 读取（getFile）：按记录自身 `storage_type` 路由（存量 V5 记录列为空按 LOCAL）。
- 后果：切换配置不影响历史记录可读性；被切换走的历史文件成为孤儿文件，本期不清理（明确不做什么）。

### 3.4 表结构（V6）

`upgrade_stock_import_v6.sql`（幂等，直接 DDL，无 PL/SQL）：

```sql
ALTER TABLE t_stock_import ADD COLUMN storage_type VARCHAR(16) DEFAULT 'LOCAL' NOT NULL;
ALTER TABLE t_stock_import ADD COLUMN file_path VARCHAR(512);
```

- `storage_type`：LOCAL-后端本地磁盘 / MINIO-MinIO 对象存储；存量行按默认值补为 LOCAL；
- `file_path`：LOCAL 为文件绝对路径；MINIO 为对象键（`stock-import/{id}.xlsx`）；
- 全新库由 payment_demo.sql 全量建表（含新列）；存量 V5 库由 init-dm8-sql.sh --if-missing
  按 V6 签名（storage_type 列是否存在）自动执行 V6 脚本。

## 4. 验收标准（结果）

1. `stock.import.storage=local`（默认）：行为与 V5 一致——文件落本地目录，导入/编辑/读取全部通过 ✅
   （StockImportServiceTest / LocalStockImportFileStoreTest 锁定）；
2. `stock.import.storage=minio` + MinIO 连接配置：导入文件写入 MinIO（对象键 `stock-import/{id}.xlsx`），
   记录回写 storage_type=MINIO 与对象键；读取按记录地址取回 ✅（Mockito 模拟 MinioClient 锁定，
   真实连通性见 §6）；
3. 存量 V5 记录（无 storage_type/file_path）读取回退 `{id}.xlsx`，不报错 ✅
   （StockImportServiceTest.get_import_file_legacy_record_fallback）；
4. storage=local 时读取 MINIO 记录（或反之）给出明确业务异常，而非 500 ✅
   （StockImportFileStorageTest.guards_when_minio_disabled_or_unknown_type）；
5. 导入/编辑/读取的对外 HTTP 接口路径与报文结构不变 ✅（StockImportControllerTest 未改断言全过）；
6. 全部受影响测试通过 ✅（mvn test：263/263）。

## 5. 测试计划（已落地）

- `StockImportServiceTest`（14 例，更新）：导入回写存储元数据（LOCAL/MINIO 两后端）、编辑按记录
  原后端写入、读取按记录地址路由、存量无地址记录回退；确认入库等原有契约不动；
- `StockImportFileStorageTest`（4 例，新增）：writeType/save/read 按 storageType 路由、大小写与空值
  归一化、MINIO 未启用报错、未知后端报错；
- `LocalStockImportFileStoreTest`（3 例，新增，@TempDir 隔离）：save 落盘返回绝对路径、read 按地址/
  回退读取、缺失返回 null、目录自动创建、保存失败包装为业务异常；
- `MinioStockImportFileStoreTest`（5 例，新增，Mockito 模拟 MinioClient，不连真实 MinIO）：对象键
  格式、bucket 不存在自动创建、save 返回对象键、read 流式读取内容、读取失败包装业务异常。

## 6. 兼容性影响与回滚

- DB：t_stock_import 新增两列，存量行有默认值，旧代码可继续运行；回滚 = 停用新代码，列冗余无害；
- 配置：`stock.import.dir` 键保留原语义；`stock.import.storage` 默认 local，行为与 V5 一致；
- 接口：对外 HTTP 契约不变（报文不变，前端无需改动）；
- 依赖：新增 io.minio:minio（连带 okhttp 4.12.0 / kotlin-stdlib 1.8.21 显式钉版本，规避 Spring Boot
  2.3 BOM 的 okhttp 3.x / kotlin 1.3 管控降级）；
- 遗留风险：真实 MinIO 连通性（endpoint/凭证/bucket 权限）需部署环境冒烟验证，单测用 Mockito 模拟。

## 7. 实现锚点

- 存储抽象：
  - 接口：`payment-demo/src/main/java/cc/ivera/storage/StockImportFileStore.java`
  - 本地：`payment-demo/src/main/java/cc/ivera/storage/LocalStockImportFileStore.java`
  - MinIO：`payment-demo/src/main/java/cc/ivera/storage/MinioStockImportFileStore.java`
  - 路由门面：`payment-demo/src/main/java/cc/ivera/storage/StockImportFileStorage.java`
- 配置：
  - `payment-demo/src/main/java/cc/ivera/config/StockImportStorageProperties.java`
  - `payment-demo/src/main/java/cc/ivera/config/StockImportStorageConfig.java`
  - `payment-demo/src/main/resources/application.yml`（`stock.import` 段）
- 服务接入：`payment-demo/src/main/java/cc/ivera/service/impl/StockImportServiceImpl.java`
  （createImport 回写地址 / saveImportFile 按记录后端覆盖 / getFile 按记录地址读取）
- 实体：`payment-demo/src/main/java/cc/ivera/entity/StockImport.java`（storageType / filePath）
- DB 脚本：
  - 升级：`payment-demo/env/sql/dm8/upgrade_stock_import_v6.sql`
  - 全量：`payment-demo/env/sql/dm8/payment_demo.sql`（t_stock_import 含新列）
  - 守卫：`payment-demo/env/scripts/dm8/init-dm8-sql.sh`（V6 签名 = user_tab_columns
    T_STOCK_IMPORT.STORAGE_TYPE；V6_ONLY / V5+V6 / V4+V5+V6 / V3~V6 增量模式；末尾 V6 校验）
- 依赖：`payment-demo/pom.xml`（io.minio:minio 8.5.7 + okhttp 4.12.0 + kotlin-stdlib 1.8.21）
- 测试：
  - `payment-demo/src/test/java/cc/ivera/service/impl/StockImportServiceTest.java`
  - `payment-demo/src/test/java/cc/ivera/storage/StockImportFileStorageTest.java`
  - `payment-demo/src/test/java/cc/ivera/storage/LocalStockImportFileStoreTest.java`
  - `payment-demo/src/test/java/cc/ivera/storage/MinioStockImportFileStoreTest.java`

## 8. 启用方式

1. 升级 DB：`bash env/scripts/dm8/init-dm8-sql.sh --if-missing`（存量 V5 库自动补 V6 两列，不动数据）；
2. 重启 Java 后端；
3. 默认 local 无需其他配置；切换 MinIO：`stock.import.storage=minio`（或环境变量
   `STOCK_IMPORT_MINIO_*` 配套）后重启。
