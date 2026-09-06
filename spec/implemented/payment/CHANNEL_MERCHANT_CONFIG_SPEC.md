# 支付渠道商户配置重构规范（Channel Merchant Config）

> State: planned
> Updated: 2026-09-06
> Issue class: Design change + Public API / DB schema / 配置来源变更
> 状态: 设计已确认（appid 放渠道、彻底移除文件配置），实现进行中

## 1. 背景与问题

改造前商户凭证存放混乱，存在**双配置源**：

| 位置 | 内容 | 问题 |
|---|---|---|
| `t_payment_app.app_config` JSON | 微信 appid/mchId/mchSerialNo/**privateKeyPath**/apiV3Key/partnerKey；支付宝 appId/sellerId/merchantPrivateKey/alipayPublicKey/returnUrl/notifyUrl | 商户信息错放在应用维度；一个商户开多个应用需重复维护密钥 |
| `t_payment_channel.config_params` JSON | 仅 domain/gatewayUrl/contentKey/notifyUrl | 渠道无商户身份 |
| `wxpay.properties` + classpath `apiclient_key.pem` + `WxPayConfig` | 文件版商户配置 + 私钥文件 + 默认 HttpClient/Verifier Bean | 第二套配置源，私钥以文件形式散落在磁盘 |
| `alipay-sandbox.properties` + `AlipayProperties` + `AlipayClientConfig` | 文件版支付宝配置 + 默认 AlipayClient Bean | 同上；默认 Bean 实际从未被业务使用（死代码） |

微信商户私钥通过 `privateKeyPath` 指向 classpath 文件加载，无法在管理端配置、多实例部署时文件分发困难。

## 2. 目标设计（已与用户确认）

1. **商户信息归渠道**：微信 `appid`、支付宝 `appId` 一并归入渠道表（用户确认）。
2. **私钥内容入库**：`apiclient_key.pem` 的 PEM 内容存入渠道表 `private_key` 列（CLOB），替代文件路径加载。
3. **彻底移除文件配置**（用户确认）：删除 `wxpay.properties`、`alipay-sandbox.properties`、`apiclient_key.pem`、`WxPayConfig`、`AlipayProperties`、`AlipayClientConfig`。商户信息唯一来源为渠道表。
4. **支付应用瘦身**：`t_payment_app` 去掉 `app_config`，应用只剩业务信息（名称/编码/状态/描述/排序/所属渠道）。

## 3. 数据模型

### 3.1 t_payment_channel 新增列

| 列 | 类型 | 说明 |
|---|---|---|
| appid | VARCHAR(64) | 微信 appid（WXPAY 渠道） |
| mch_id | VARCHAR(32) | 微信商户号 |
| mch_serial_no | VARCHAR(64) | 微信商户 API 证书序列号 |
| private_key | CLOB | 微信商户私钥 PEM 内容（`\\n` 转义或真实换行，代码侧归一化） |
| api_v3_key | VARCHAR(128) | 微信 APIv3 密钥 |
| partner_key | VARCHAR(128) | 微信 APIv2 密钥 |
| alipay_app_id | VARCHAR(64) | 支付宝应用 ID（ALIPAY 渠道） |
| seller_id | VARCHAR(64) | 支付宝卖家 PID |
| merchant_private_key | CLOB | 支付宝应用私钥（base64） |
| alipay_public_key | CLOB | 支付宝公钥（base64） |

`config_params` JSON 继续承载渠道公共参数：`domain`、`gatewayUrl`、`contentKey`、`notifyUrl`、`returnUrl`（原应用级 returnUrl/notifyUrl 迁入渠道 config_params）。

### 3.2 t_payment_app

删除 `app_config` 列；种子数据应用行不再携带 JSON。

### 3.3 存量库迁移（ALTER，见 §7）

```sql
-- t_payment_channel 增列（10 个）+ UPDATE 回填种子值
-- t_payment_app DROP COLUMN app_config
```

## 4. 运行时配置链路

- `PaymentConfigLoader.convertToConfig`：商户字段全部取自渠道实体新列；公共参数仍取 `config_params` JSON；不再解析应用 JSON。
- `PaymentAppConfig.privateKeyPath` → `privateKey`（内容）；私钥解析统一走 `WxPayPrivateKeyUtil.load(content)`（归一化 `\\n` 转义后 `PemUtil.loadPrivateKey`）。
- 私钥消费点全部改为内容加载：`WxPayHttpClient`（签名/无签名客户端）、`WxPayNotifyHandler`（回调验签 Verifier）、`WxPayOrderService` JSAPI 签名。
- 客户端/Verifier 缓存 key 由 `privateKeyPath` 改为 `privateKey` 内容 hash。

## 5. 文件配置体系移除清单

- 删除：`config/WxPayConfig.java`、`config/AlipayProperties.java`、`config/AlipayClientConfig.java`、`resources/wxpay.properties`、`resources/alipay-sandbox.properties`、`resources/apiclient_key.pem`。
- 删除 3 处 `buildDefaultWxPayConfig()`/`buildDefaultAliPayConfig()` 文件兜底：DB 无可用配置时支付接口直接抛"支付渠道未配置或未启用"（语义更诚实，不再静默回退）。
- `WxPayV2Controller` 回调验签 partnerKey：改为 订单→支付应用→渠道默认 链路取渠道表 `partner_key`。
- `WxPayNotifyHandler`：移除文件版默认 Verifier Bean 依赖，仅按渠道配置构建 Verifier。
- `TestController`：商户号改读 PaymentConfigLoader 渠道默认配置。
- `WxPayNotificationDecoder`：APIv3 密钥候选仅来自渠道配置。
- 清理死代码：`WxPayHttpClient` 无参客户端重载与默认 Bean 注入、`AliPayServiceImpl` 未使用的默认 `AlipayClient` 注入。

## 6. 兼容性影响

| 项 | 影响 |
|---|---|
| DB schema | t_payment_channel 增列、t_payment_app 删列，需重建库或执行 §7 ALTER |
| 管理端 API | 渠道创建/更新/详情请求响应新增 10 个商户字段；应用请求/响应/视图 VO 移除 appConfig |
| 管理端前端 | 渠道表单新增商户信息分组（微信/支付宝）；应用表单移除"应用参数JSON" |
| 启动行为 | 无文件兜底：DB 无启用渠道时支付下单/回调验签失败并明确报错；`PaymentConfigLoader` 空库启动容错逻辑保留 |
| 安全 | 商户私钥随渠道实体经管理端接口回显（演示项目现状即回显 appConfig 密钥，维持）；`apiclient_key.pem` 不再存在于文件系统 |

## 7. 存量库 ALTER 脚本

```sql
ALTER TABLE t_payment_channel ADD appid VARCHAR(64);
ALTER TABLE t_payment_channel ADD mch_id VARCHAR(32);
ALTER TABLE t_payment_channel ADD mch_serial_no VARCHAR(64);
ALTER TABLE t_payment_channel ADD private_key CLOB;
ALTER TABLE t_payment_channel ADD api_v3_key VARCHAR(128);
ALTER TABLE t_payment_channel ADD partner_key VARCHAR(128);
ALTER TABLE t_payment_channel ADD alipay_app_id VARCHAR(64);
ALTER TABLE t_payment_channel ADD seller_id VARCHAR(64);
ALTER TABLE t_payment_channel ADD merchant_private_key CLOB;
ALTER TABLE t_payment_channel ADD alipay_public_key CLOB;
UPDATE t_payment_channel SET appid='wx74862e0dfcf69954', mch_id='1558950191', mch_serial_no='34345964330B66427E0D3D28826C4993C77E631F', private_key='(PEM内容，\\n转义)', api_v3_key='UDuLFDcmy5Eb6o0nTNZdu6ek4DDh4K8B', partner_key='T6m9iK73b0kn9g5v426MKfHQH7X8rKwb' WHERE channel_code='WXPAY';
UPDATE t_payment_channel SET alipay_app_id='9021000136667568', seller_id='2088721034748965', merchant_private_key='(与原 app_config JSON 中 merchantPrivateKey 一致)', alipay_public_key='(与原 app_config JSON 中 alipayPublicKey 一致)' WHERE channel_code='ALIPAY';
ALTER TABLE t_payment_app DROP COLUMN app_config;
```

## 8. 验收标准

1. [x] `mvn compile` 通过；全库无 `WxPayConfig`/`AlipayProperties`/`privateKeyPath` 残留引用。
2. [x] 仅从 DB 渠道表读取商户凭证；资源目录无 wxpay.properties/alipay-sandbox.properties/apiclient_key.pem（resources 仅剩 application.yml）。
3. [x] `t_payment_channel` 种子数据含完整微信/支付宝商户信息（含 PEM 私钥内容）；`t_payment_app` 无 app_config（种子 INSERT 与建表均已移除）。
4. [x] 管理端渠道表单可维护商户信息，应用表单不再出现 appConfig（vue-admin 与 react-admin 均已改造）。
5. [x] 微信 V3 下单/回调验签、V2 回调验签、支付宝下单/回调均从渠道配置取参数。

### 验收证据（2026-09-06）

- 后端：`mvn compile` BUILD SUCCESS（exit 0）。
- react-admin：`npm run build` → `✓ built in 3.70s`。
- vue-admin：`npm run build` → `DONE  Build complete. The dist directory is ready to be deployed.`。
- 残留扫描：`WxPayConfig`/`AlipayProperties`/`privateKeyPath` 在 src/main/java 无引用；admin 前端无 `appConfig` 引用。
- 存量库迁移脚本：`payment-demo/env/sql/dm8/upgrade_channel_merchant_config.sql`（ALTER 增列 + 商户数据回填 + DROP app_config）。

## 9. 实现锚点

| 组件 | 文件 |
|---|---|
| 渠道实体/请求 | `payment-demo/src/main/java/cc/ivera/entity/PaymentChannel.java`、`dto/PaymentChannelRequest.java` |
| 应用实体/请求/视图 | `entity/PaymentApp.java`、`dto/PaymentAppRequest.java`、`vo/PaymentAppViewVO.java`、`controller/PaymentAppController.java` |
| 配置加载 | `config/PaymentConfigLoader.java`、`config/PaymentAppConfig.java` |
| 私钥解析 | `util/WxPayPrivateKeyUtil.java`（新增） |
| 微信链路 | `service/impl/wxpay/WxPayHttpClient.java`、`WxPayOrderService.java`、`WxPayRefundService.java`、`WxPayBillService.java`、`WxPayNotificationDecoder.java`、`controller/support/WxPayNotifyHandler.java`、`controller/WxPayV2Controller.java` |
| 支付宝链路 | `service/impl/AliPayServiceImpl.java` |
| 测试控制器 | `controller/TestController.java` |
| DB | `payment-demo/env/sql/dm8/payment_demo.sql`、`payment-demo/env/sql/dm8/upgrade_channel_merchant_config.sql`（存量库迁移，新增） |
| 前端 | `payment-demo-vue-admin/src/views/PaymentConfig.vue`、`payment-demo-react-admin/src/pages/PaymentConfig.jsx` |

## 10. Change Log

- 2026-09-06：规范创建（设计确认：appid 归渠道、私钥内容入库、彻底移除文件配置）。
- 2026-09-06：实现落地。渠道实体/请求/服务层新增 10 个商户字段并全部接入加载与复制链路；应用实体/请求/视图 VO 移除 appConfig；微信/支付宝全链路改为渠道表取参，私钥统一 `WxPayPrivateKeyUtil` 内容加载；删除 WxPayConfig/AlipayProperties/AlipayClientConfig/wxpay.properties/alipay-sandbox.properties/apiclient_key.pem；payment_demo.sql 渠道表增列+种子 PEM 入库、应用表去 app_config；新增存量库 ALTER 脚本 upgrade_channel_merchant_config.sql；vue-admin/react-admin 渠道表单新增微信/支付宝商户分组、应用表单去 appConfig。双端构建与 mvn compile 全部通过，规范移入 implemented。
