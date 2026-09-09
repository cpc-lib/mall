# 达梦 DM8 运维与初始化说明

## 1. 启动 DM8

```bash
bash env/scripts/dm8/start-dm8.sh
```

脚本启动容器后会自动校验 6 张必需业务表（`t_payment_channel`、`t_payment_app`、`t_order_info`、`t_payment_info`、`t_product`、
`t_refund_info`）：仅当有表缺失（全新或不完整 schema）时才自动执行初始化 SQL；表齐全时直接跳过，不触碰已有数据。看到
`DM8 就绪：必需业务表校验通过` 后再启动后端。

默认使用：

```text
image=registry.cn-hangzhou.aliyuncs.com/snow-io/dm8:latest
container=payment-demo-dm8
port=5236
username=SYSDBA
password=Cpc2026#@Dm
schema=SYSDBA
```

## 2. 初始化 SQL

```bash
bash env/scripts/dm8/init-dm8-sql.sh
```

脚本会按文件名顺序执行容器内 `/dm8-init/*.sql`。宿主机对应目录是：

```text
env/sql/dm8
```

带 `--if-missing` 参数时，脚本先检测必需业务表：齐全则直接跳过（不破坏已有数据），缺失才执行破坏性重建。`start-dm8.sh`
内部正是以该方式自动调用的。

## 3. 登录验证

```bash
docker exec -it payment-demo-dm8 bash
cd /opt/dmdbms/bin
./disql SYSDBA/'Cpc2026#@Dm'
```

## 4. 应用连接

`src/main/resources/application.yml` 默认连接：

```yaml
spring:
  datasource:
    driver-class-name: dm.jdbc.driver.DmDriver
    url: jdbc:dm://${DM_HOST:192.168.1.200}:${DM_PORT:5236}?schema=${DM_SCHEMA:SYSDBA}&connectTimeout=30000
    username: ${DM_USERNAME:SYSDBA}
    password: ${DM_PASSWORD:Cpc2026#@Dm}
```

## 5. 清库重建

谨慎执行，下面命令会删除 `dm8-data` 数据卷：

```bash
docker compose -f env/docker-compose.dm8.yml down -v
bash env/scripts/dm8/start-dm8.sh
bash env/scripts/dm8/init-dm8-sql.sh
```

## 6. 常见问题

### password must between 9 and 48

达梦初始化密码必须 9 到 48 位。当前默认密码 `Cpc2026#@Dm` 满足要求。

### [CHARSET] value error

启动参数需要设置字符集。当前 compose 同时配置了：

```yaml
CHARSET: "1"
UNICODE_FLAG: "1"
```

### file dm.key not found

这是试用 license 提示，不是数据库初始化失败的根因。

### 无效的表或视图名 t_payment_channel

这表示后端当前连接的 DM8 schema 中没有运行时配置表。请确认应用 `application.yml` 或环境变量中的 `DM_HOST`、`DM_PORT`、
`DM_SCHEMA`、`DM_USERNAME` 与执行初始化 SQL 的数据库实例一致。

不要通过开启 MySQL 兼容模式处理这个问题。本项目的 DM8 初始化脚本使用达梦原生兼容的 `USER_TABLES` 存在性检查和标准 DDL。

初始化脚本结束前会校验 `t_payment_channel`、`t_payment_app`、`t_order_info`、`t_payment_info`、`t_product`、`t_refund_info`
六张表；只有校验通过才会输出 `DM8 SQL 初始化完成。`

后端启动时若支付配置表不可用，会以空的 DB 配置缓存继续并打印简短告警；商品、订单、支付流水、退款和配置管理流程仍依赖这些运行时表，必须先对应用实际连接的
DM8 schema 执行完整初始化 SQL。

如果库中已有业务数据，不要直接运行完整初始化脚本清库重建；应先备份或拆分出只补缺失对象的迁移脚本。
