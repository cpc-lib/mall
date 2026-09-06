# Payment Demo- 支付集成与并发控制演示项目

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java Version](https://img.shields.io/badge/Java-1.8-orange.svg)](https://www.oracle.com/java/technologies/javase-jdk8-downloads.html)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## 📋 项目简介

Payment Demo 是一个完整的支付集成演示项目，集成了**微信支付 V2/V3** 和**支付宝**，并实现了企业级的**并发控制**与**幂等性**保障机制。该项目适合学习和参考如何在生产环境中安全、可靠地处理支付业务。

## ✨ 核心特性

### 💰 支付功能
- ✅ **微信支付 V3**：扫码支付、JSAPI支付、退款、订单查询、对账单下载
- ✅ **微信支付 V2**：扫码支付、支付通知处理
- ✅ **支付宝**：扫码支付、退款、订单查询
- ✅ **订单管理**：创建订单、取消订单、查询订单
- ✅ **退款管理**：退款申请、审核通过/拒绝、退款查询、订单对账
- ✅ **账单对账**：管理员上传微信交易账单 CSV（支持 ALL 全部 / SUCCESS 支付成功 / REFUND 退款三种账单，自动识别），解析入库后自动与本地支付/退款记录对账，差异单可人工标记处理；同文件/同账单日/重复对账全链路幂等

### 🔒 并发控制 (核心升级)
- ✅ **三层并发控制架构**
  - 第一层：通知幂等检查（Redis + notifyId）
  - 第二层：Redisson 分布式锁（看门狗自动续期）
  - 第三层：数据库行锁 + 状态条件更新（CAS）
- ✅ **Redisson 分布式锁**：支持看门狗机制，防止锁提前过期
- ✅ **通知幂等处理**：防止重复处理支付/退款通知
- ✅ **数据库唯一约束**：防止重复数据
- ✅ **乐观锁**：订单表支持版本号控制

## 🚀 快速开始

### 环境要求

- JDK 1.8+
- Maven 3.6+
- 达梦数据库 DM8
- Redis 5.0+
- RabbitMQ 3.7+

### 安装步骤

#### 1. 克隆项目

```bash
git clone <repository-url>
cd payment-demo-v6-concurrency-final
```

#### 2. 启动 DM8 并初始化数据库

本项目已将默认数据库从 达梦 DM8 8 替换为达梦 DM8。先启动 DM8：

```bash
bash env/scripts/dm8/start-dm8.sh
```

确认容器启动后执行初始化 SQL：

```bash
bash env/scripts/dm8/init-dm8-sql.sh
```

停止 DM8：

```bash
bash env/scripts/dm8/stop-dm8.sh
```

默认连接信息：

```text
url=jdbc:dm://192.168.1.200:5236?schema=SYSDBA&connectTimeout=30000
username=SYSDBA
password=Cpc2026#@Dm
```

#### 3. 配置项目

修改 `src/main/resources/application.yml` 中的达梦、Redis、RabbitMQ 连接信息：

```yaml
spring:
  datasource:
    driver-class-name: dm.jdbc.driver.DmDriver
    url: jdbc:dm://${DM_HOST:192.168.1.200}:${DM_PORT:5236}?schema=${DM_SCHEMA:SYSDBA}&connectTimeout=30000
    username: ${DM_USERNAME:SYSDBA}
    password: ${DM_PASSWORD:Cpc2026#@Dm}

  redis:
    host: your-redis-host
    port: 6379

  rabbitmq:
    host: your-rabbitmq-host
    port: 5672
    username: guest
    password: guest
```

RabbitMQ 队列、exchange、routing key 和延迟参数的部署/运维清单见 [docs/RABBITMQ_OPERATIONS.md](docs/RABBITMQ_OPERATIONS.md)。

#### 4. 配置支付信息

**微信支付配置** (`src/main/resources/wxpay.properties`)：
```properties
# 配置你的微信支付信息
```

**支付宝配置** (`src/main/resources/alipay-sandbox.properties`)：
```properties
# 配置你的支付宝信息
```

#### 5. 启动项目

```bash
mvn clean install
mvn spring-boot:run
```

项目启动后，访问：
- 应用地址：http://localhost:8080
- Swagger 文档：http://localhost:8080/swagger-ui.html

## 📁 项目结构

```
payment-demo-v5/
├── env/
│   ├── docker-compose.dm8.yml         # 达梦 DM8 docker-compose
│   ├── scripts/dm8/                   # DM8 启停与初始化脚本
│   └── sql/
│       └── dm8/                       # 达梦 DM8 初始化脚本
│           └── 001_payment_demo_dm8.sql
├── src/
│   ├── main/
│   │   ├── java/cc/ivera/
│   │   │   ├── config/               # 配置类
│   │   │   │   ├── RedissonConfig.java          # Redisson 配置
│   │   │   │   ├── WxPayConfig.java             # 微信支付配置
│   │   │   │   └── AlipayClientConfig.java      # 支付宝配置
│   │   │   ├── controller/           # 控制器
│   │   │   │   ├── WxPayController.java         # 微信支付 V3
│   │   │   │   ├── WxPayV2Controller.java       # 微信支付 V2
│   │   │   │   ├── AliPayController.java        # 支付宝
│   │   │   │   ├── RefundInfoController.java    # 退款管理
│   │   │   │   └── support/WxPayNotifyHandler.java  # 微信通知处理
│   │   │   ├── service/              # 业务服务层
│   │   │   │   ├── wxpay/            # 微信支付服务
│   │   │   │   └── refund/           # 退款服务
│   │   │   ├── lock/                 # 分布式锁
│   │   │   │   ├── DistributedLockTemplate.java
│   │   │   │   ├── RedissonDistributedLockTemplate.java  # Redisson 锁实现
│   │   │   │   └── RedisDistributedLockTemplate.java     # Redis 锁实现
│   │   │   ├── entity/               # 实体类
│   │   │   ├── mapper/               # MyBatis Mapper
│   │   │   └── util/                 # 工具类
│   │   └── resources/
│   │       ├── application.yml       # 应用配置
│   │       └── mapper/               # MyBatis XML
│   └── test/                         # 测试
├── pom.xml                           # Maven 配置
└── README.md                         # 项目文档
```

## 🔑 并发控制详解

### 三层并发控制架构

```
┌─────────────────────────────────────────────────────────────┐
│  第一层：通知幂等检查                                        │
│  Redis + notifyId，24小时过期，防止重复处理通知              │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  第二层：Redisson 分布式锁                                   │
│  看门狗自动续期，防止锁在业务执行中过期                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  第三层：数据库行锁 + 状态条件更新                           │
│  SELECT ... FOR UPDATE + UPDATE ... WHERE status = ...     │
└─────────────────────────────────────────────────────────────┘
```

### Redisson 分布式锁 (看门狗机制)

```java
// 使用示例
distributedLockTemplate.execute(lockKey, waitTime, leaseTime, () -> {
    // 业务逻辑
    // 当 leaseTime = -1 时，看门狗自动续期，默认30秒过期，每10秒续期
    return result;
});
```

### 数据库唯一约束

| 表 | 约束 | 作用 |
|----|------|------|
| `t_order_info` | `uk_order_no` | 防止商户订单号重复 |
| `t_payment_info` | `uk_order_payment_type` | 防止同一订单同一支付方式重复写支付流水 |
| `t_payment_info` | `uk_transaction_id_payment_type` | 防止同一渠道交易号重复写支付流水 |
| `t_refund_info` | `uk_refund_no` | 防止商户退款单号重复 |
| `t_refund_info` | `uk_refund_id` | 防止渠道退款单号重复 |

## 📖 API 文档

### 主要接口

#### 微信支付 V3

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/wx-pay/native/{productId}` | POST | 微信扫码支付下单 |
| `/api/wx-pay/native/notify` | POST | 支付结果通知 |
| `/api/wx-pay/jsapi/{productId}/{openid}` | POST | JSAPI 支付下单 |
| `/api/wx-pay/jsapi/notify/v1` | POST | JSAPI 支付通知 |
| `/api/wx-pay/refunds/{orderNo}/{reason}` | POST | 申请退款 |
| `/api/wx-pay/refunds/notify` | POST | 退款结果通知 |
| `/api/wx-pay/cancel/{orderNo}` | POST | 取消订单 |
| `/api/wx-pay/check-order-status/{orderNo}` | GET | 主动查询并同步支付状态 |

#### 微信支付 V2

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/wx-pay-v2/native/{productId}/{remoteAddr}` | POST | 微信 V2 扫码支付下单 |
| `/api/wx-pay-v2/native/notify` | POST | 支付结果通知 |

#### 退款管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/refund-info/apply` | POST | 申请退款 |
| `/api/refund-info/approve/{refundNo}` | POST | 审核通过 |
| `/api/refund-info/reject/{refundNo}` | POST | 审核拒绝 |
| `/api/refund-info/query/{refundNo}` | GET | 查询退款状态 |
| `/api/refund-info/reconcile/{orderNo}` | POST | 订单对账 |

#### 账单对账（仅管理员）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/reconciliation/bill/upload` | POST | 上传微信交易账单 CSV（multipart：file、billDate、billType=tradebill），解析入库并自动对账；同文件 hash 幂等，同日同种类不可重复上传 |
| `/api/reconciliation/imports` | GET | 对账批次列表（可按 billDate 过滤） |
| `/api/reconciliation/imports/{importNo}` | GET | 批次详情 |
| `/api/reconciliation/imports/{importNo}/records` | GET | 账单流水列表（可按 recordType=PAY/REFUND 过滤） |
| `/api/reconciliation/imports/{importNo}/discrepancies` | GET | 对账差异列表（可按 bizType/discrepancyType/status 过滤） |
| `/api/reconciliation/imports/{importNo}/reconcile` | POST | 重新对账（RECONCILED 幂等直返，失败批次可重跑） |
| `/api/reconciliation/discrepancies/{id}/resolve` | POST | 标记差异已处理（JSON body：resolveRemark） |

> 账单文件获取：微信支付商户平台下载交易账单（ALL/SUCCESS/REFUND），或通过「账单下载」页跳转微信/支付宝账单接口；对账链路与下载链路相互独立。

更多接口请访问：http://localhost:8080/swagger-ui.html

## 🔧 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 2.3.7 | 应用框架 |
| MyBatis Plus | 3.3.1 | ORM 框架 |
| 达梦 DM8 | 8.x | 数据库 |
| Redis | 5.0+ | 缓存/分布式锁 |
| Redisson | 3.16.8 | 高级分布式锁 |
| RabbitMQ | 3.7+ | 消息队列 |
| Swagger | 2.7.0 | API 文档 |
| WechatPay APIv3 | 0.3.0 | 微信支付 SDK |
| WechatPay APIv2 | 0.0.3 | 微信支付 V2 SDK |
| Alipay SDK | 4.22.57 | 支付宝 SDK |

## 📊 项目版本演进

| 版本 | 主要特性 |
|------|----------|
| V1/V2 | 基础支付功能，微信/支付宝集成 |
| V3 | 幂等性增强，数据库唯一约束，乐观锁 |
| V4/V5 | 并发控制增强，通知幂等检查 |
| V6 (当前) | Redisson 分布式锁 + 看门狗，全面并发控制 |

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 🙏 致谢

- 微信支付官方文档
- 支付宝官方文档
- Redisson 官方文档

---

**如果这个项目对你有帮助，请给个 Star ⭐️**
