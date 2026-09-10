-- ===============================================================
-- 电商商城平台 DM8 一体化完整初始化脚本（单一文件，纯 DM8 语法）
-- ===============================================================
-- 内容：
--   PART 1  核心业务库（20 张表 + 种子数据，历次增量升级结构已全部固化）
--   PART 2  省市区基础数据（t_region 表 + 全国三级行政区划）
--   PART 3  用户收货地址表（t_shipping_address）
--
-- 本脚本为破坏性重建：先按依赖顺序 DROP 再 CREATE，并写入初始化数据；
-- 可重复执行（等同清库重建）。
-- ===============================================================
-- ===============================================================
-- 电商商城平台 DM8 核心业务库初始化脚本（V3/V4/V5/V6 已整合，单一全量脚本，纯 DM8 语法）
-- 覆盖范围：支付渠道/应用、订单与多商品明细快照、支付流水、退款单与
--           用户分项退款申请、用户中心、找回密码申请、商品库存与可售状态、
--           库存操作幂等日志、微信交易账单上传式对账（导入批次/账单流水/对账差异）。
-- 本脚本为破坏性重建：先按依赖顺序 DROP 再 CREATE，并写入初始化数据。
-- 已整合：upgrade_admin_auth_v3.sql / upgrade_stock_audit_v4.sql /
--         upgrade_stock_import_v5.sql / upgrade_stock_import_v6.sql。
-- 注意：上述增量脚本的最终结构已经固化到本全量脚本中，切勿在本脚本后再次执行，
--       否则会产生重复建表、重复加列或重复建索引错误。
-- 已合并原增量脚本 upgrade_user_cart_order_refund_stock.sql 的全部内容
-- （t_user / t_order_item / t_refund_apply / t_refund_apply_item，
--   以及 t_product 的 stock / product_status 列），
-- 并已并入库存货损四桶增量（原 upgrade_add_lost_stock.sql：t_product.lost_stock、
-- t_inventory_transaction.lost_delta）与旧表清理（原 upgrade_drop_stock_operation_log.sql
-- 的 DROP 守卫），两增量脚本已移除，本目录仅保留本文件一个 SQL。
-- 全新初始化无需再执行任何增量升级脚本；原增量脚本中“旧库历史订单明细
-- 迁移”语句仅服务于已存在数据的旧库，不属于全量重建范围，故不纳入。
-- 对账功能：旧自动拉单式对账三表（t_reconciliation_batch/detail/discrepancy）
-- 已废弃，仅保留 DROP 守卫块清理遗留库，不再重建；新对账为“微信交易账单
-- 上传式对账”，使用 t_bill_import / t_bill_record /
-- t_bill_reconcile_discrepancy 三张表。
-- V1 库存操作日志表 t_stock_operation_log 同已废弃，仅保留 DROP 守卫块
-- 清理遗留库（含全量重建场景），不再重建；存量库如需单独清理，手动执行
-- DROP TABLE t_stock_operation_log CASCADE; 即可。
-- 运行方式：docker compose 启动 DM8 后执行 env/scripts/dm8/init-dm8-sql.sh，
--           该脚本会按文件名顺序执行 env/sql/dm8 下的 *.sql。
-- 默认连接用户：SYSDBA；默认 schema：SYSDBA。
-- ===============================================================

-- ----------------------------
-- Drop tables in dependency-safe order.
-- 直接 DDL（非 PL/SQL），每条 ; 结尾；使用 IF EXISTS，首次执行及重复执行均不会因表不存在报错。
-- ----------------------------
DROP TABLE IF EXISTS t_local_message CASCADE;
DROP TABLE IF EXISTS t_order_shipment CASCADE;
DROP TABLE IF EXISTS t_inventory_transaction CASCADE;
DROP TABLE IF EXISTS t_stock_import CASCADE;
DROP TABLE IF EXISTS t_inventory_reservation CASCADE;
DROP TABLE IF EXISTS t_payment_order CASCADE;
DROP TABLE IF EXISTS t_refund_item CASCADE;
DROP TABLE IF EXISTS t_refund_order CASCADE;
DROP TABLE IF EXISTS t_schema_migration CASCADE;
DROP TABLE IF EXISTS t_refund_apply_item CASCADE;
DROP TABLE IF EXISTS t_refund_apply CASCADE;
DROP TABLE IF EXISTS t_order_item CASCADE;
DROP TABLE IF EXISTS t_password_reset_request CASCADE;
DROP TABLE IF EXISTS t_user CASCADE;
DROP TABLE IF EXISTS t_refund_info CASCADE;
DROP TABLE IF EXISTS t_payment_info CASCADE;
DROP TABLE IF EXISTS t_order_info CASCADE;
DROP TABLE IF EXISTS t_payment_app CASCADE;
DROP TABLE IF EXISTS t_product CASCADE;
DROP TABLE IF EXISTS t_payment_channel CASCADE;
DROP TABLE IF EXISTS t_reconciliation_discrepancy CASCADE;
DROP TABLE IF EXISTS t_reconciliation_detail CASCADE;
DROP TABLE IF EXISTS t_reconciliation_batch CASCADE;
DROP TABLE IF EXISTS t_stock_operation_log CASCADE;
DROP TABLE IF EXISTS t_bill_reconcile_discrepancy CASCADE;
DROP TABLE IF EXISTS t_bill_record CASCADE;
DROP TABLE IF EXISTS t_bill_import CASCADE;

-- ----------------------------
-- t_payment_channel 支付渠道配置表
-- ----------------------------
CREATE TABLE t_payment_channel
(
    id                   BIGINT IDENTITY(1, 1) NOT NULL,
    channel_name         VARCHAR(64)                   NOT NULL,
    channel_code         VARCHAR(32)                   NOT NULL,
    channel_status       VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
    channel_desc         VARCHAR(255),
    config_params        CLOB,
    appid                VARCHAR(64),
    mch_id               VARCHAR(32),
    mch_serial_no        VARCHAR(64),
    private_key          CLOB,
    api_v3_key           VARCHAR(128),
    partner_key          VARCHAR(128),
    alipay_app_id        VARCHAR(64),
    seller_id            VARCHAR(64),
    merchant_private_key CLOB,
    alipay_public_key    CLOB,
    sort_order           INT         DEFAULT 0         NOT NULL,
    create_time          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_channel PRIMARY KEY (id),
    CONSTRAINT uk_channel_code UNIQUE (channel_code)
);

COMMENT
ON TABLE t_payment_channel IS '支付渠道配置表：保存渠道级公共参数与商户信息（含商户私钥内容）';
COMMENT
ON COLUMN t_payment_channel.id IS '支付渠道ID';
COMMENT
ON COLUMN t_payment_channel.channel_name IS '渠道名称，例如微信支付、支付宝';
COMMENT
ON COLUMN t_payment_channel.channel_code IS '渠道编码：WXPAY、ALIPAY';
COMMENT
ON COLUMN t_payment_channel.channel_status IS '渠道状态：ENABLED-启用，DISABLED-禁用';
COMMENT
ON COLUMN t_payment_channel.channel_desc IS '渠道描述';
COMMENT
ON COLUMN t_payment_channel.config_params IS '渠道公共参数JSON字符串，例如domain、gatewayUrl、contentKey、notifyUrl、returnUrl';
COMMENT
ON COLUMN t_payment_channel.appid IS '微信appid（公众号/小程序/APP），WXPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.mch_id IS '微信商户号，WXPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.mch_serial_no IS '微信商户API证书序列号，WXPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.private_key IS '微信商户私钥PEM内容（\n转义或真实换行，代码侧归一化），替代apiclient_key.pem文件';
COMMENT
ON COLUMN t_payment_channel.api_v3_key IS '微信APIv3密钥，WXPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.partner_key IS '微信APIv2密钥，WXPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.alipay_app_id IS '支付宝应用ID，ALIPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.seller_id IS '支付宝卖家PID，ALIPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.merchant_private_key IS '支付宝应用私钥（base64），ALIPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.alipay_public_key IS '支付宝公钥（base64），ALIPAY渠道使用';
COMMENT
ON COLUMN t_payment_channel.sort_order IS '排序号';
COMMENT
ON COLUMN t_payment_channel.create_time IS '创建时间';
COMMENT
ON COLUMN t_payment_channel.update_time IS '更新时间';

CREATE INDEX idx_channel_status_sort ON t_payment_channel (channel_status, sort_order);

CREATE
OR REPLACE TRIGGER trg_payment_channel_uptime
BEFORE
UPDATE ON t_payment_channel
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_payment_app 支付应用配置表
-- ----------------------------
CREATE TABLE t_payment_app
(
    id          BIGINT IDENTITY(1, 1) NOT NULL,
    app_name    VARCHAR(64)                   NOT NULL,
    app_code    VARCHAR(64)                   NOT NULL,
    app_status  VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
    channel_id  BIGINT                        NOT NULL,
    app_desc    VARCHAR(255),
    sort_order  INT         DEFAULT 0         NOT NULL,
    create_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_app PRIMARY KEY (id),
    CONSTRAINT uk_app_code UNIQUE (app_code),
    CONSTRAINT fk_payment_app_channel FOREIGN KEY (channel_id) REFERENCES t_payment_channel (id)
);

COMMENT
ON TABLE t_payment_app IS '支付应用配置表：仅保存应用业务信息，商户密钥统一保存在渠道表';
COMMENT
ON COLUMN t_payment_app.id IS '支付应用ID';
COMMENT
ON COLUMN t_payment_app.app_name IS '应用名称';
COMMENT
ON COLUMN t_payment_app.app_code IS '应用编码';
COMMENT
ON COLUMN t_payment_app.app_status IS '应用状态：ENABLED-启用，DISABLED-禁用';
COMMENT
ON COLUMN t_payment_app.channel_id IS '关联支付渠道ID';
COMMENT
ON COLUMN t_payment_app.app_desc IS '应用描述';
COMMENT
ON COLUMN t_payment_app.sort_order IS '排序号';
COMMENT
ON COLUMN t_payment_app.create_time IS '创建时间';
COMMENT
ON COLUMN t_payment_app.update_time IS '更新时间';

CREATE INDEX idx_app_channel_status_sort ON t_payment_app (channel_id, app_status, sort_order);

CREATE
OR REPLACE TRIGGER trg_payment_app_uptime
BEFORE
UPDATE ON t_payment_app
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_order_info 订单表（V2 四维状态模型）
-- order_status: WAIT_PAY/ACTIVE/CLOSED/COMPLETED
-- pay_status: UNPAID/PAID
-- fulfillment_status: WAIT_SHIP/SHIPPED/RECEIVED/CANCELLED
-- refund_status: NONE/REFUNDING/PARTIAL_REFUNDED/FULL_REFUNDED
-- legacy_status 保留 V1 单一状态值（NOTPAY/SUCCESS/...）仅供审计/回滚
-- ----------------------------
CREATE TABLE t_order_info
(
    id                   BIGINT IDENTITY(1, 1) NOT NULL,
    title                VARCHAR(256),
    order_no             VARCHAR(50)                     NOT NULL,
    user_id              BIGINT,
    product_id           BIGINT                          NOT NULL,
    total_fee            INT                             NOT NULL,
    code_url             VARCHAR(512),
    legacy_status        VARCHAR(30),
    order_status         VARCHAR(32) DEFAULT 'WAIT_PAY'  NOT NULL,
    pay_status           VARCHAR(32) DEFAULT 'UNPAID'    NOT NULL,
    fulfillment_status   VARCHAR(32) DEFAULT 'WAIT_SHIP' NOT NULL,
    refund_status        VARCHAR(32) DEFAULT 'NONE'      NOT NULL,
    paid_amount          INT         DEFAULT 0           NOT NULL,
    refund_frozen_amount INT         DEFAULT 0           NOT NULL,
    refunded_amount      INT         DEFAULT 0           NOT NULL,
    expire_time          TIMESTAMP,
    paid_time            TIMESTAMP,
    receiver_name        VARCHAR(64),
    receiver_phone       VARCHAR(32),
    receiver_address     VARCHAR(255),
    create_time          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    payment_type         VARCHAR(20)                     NOT NULL,
    payment_app_id       BIGINT,
    payment_channel_code VARCHAR(32),
    version              INT         DEFAULT 0           NOT NULL,
    CONSTRAINT pk_order_info PRIMARY KEY (id),
    CONSTRAINT uk_order_no UNIQUE (order_no)
);

COMMENT
ON TABLE t_order_info IS '订单表：V2 四维状态（交易/支付/履约/退款），乐观锁 version，退款冻结三层防线之订单层';
COMMENT
ON COLUMN t_order_info.id IS '订单id';
COMMENT
ON COLUMN t_order_info.title IS '订单标题';
COMMENT
ON COLUMN t_order_info.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_order_info.user_id IS '用户id';
COMMENT
ON COLUMN t_order_info.product_id IS '主商品id（兼容旧字段，真实商品组成以 t_order_item 为准）';
COMMENT
ON COLUMN t_order_info.total_fee IS '应付金额(分)';
COMMENT
ON COLUMN t_order_info.code_url IS '订单二维码连接';
COMMENT
ON COLUMN t_order_info.legacy_status IS 'V1 旧状态值，仅供审计/回滚对照';
COMMENT
ON COLUMN t_order_info.order_status IS '交易状态：WAIT_PAY-待支付，ACTIVE-有效，CLOSED-已关闭，COMPLETED-已完成';
COMMENT
ON COLUMN t_order_info.pay_status IS '支付状态：UNPAID-未支付，PAID-已支付';
COMMENT
ON COLUMN t_order_info.fulfillment_status IS '履约状态：WAIT_SHIP-待发货，SHIPPED-已发货，RECEIVED-已收货，CANCELLED-已取消';
COMMENT
ON COLUMN t_order_info.refund_status IS '退款汇总状态：NONE-无退款，REFUNDING-退款中，PARTIAL_REFUNDED-部分退款，FULL_REFUNDED-全额退款';
COMMENT
ON COLUMN t_order_info.paid_amount IS '有效实付金额(分)';
COMMENT
ON COLUMN t_order_info.refund_frozen_amount IS '退款申请冻结金额(分)：已占用但尚未完成';
COMMENT
ON COLUMN t_order_info.refunded_amount IS '已成功退款金额(分)';
COMMENT
ON COLUMN t_order_info.expire_time IS '订单过期时间（超时关单/库存预占释放边界）';
COMMENT
ON COLUMN t_order_info.paid_time IS '支付成功时间';
COMMENT
ON COLUMN t_order_info.receiver_name IS '收货人姓名（物流模拟）';
COMMENT
ON COLUMN t_order_info.receiver_phone IS '收货人电话（物流模拟）';
COMMENT
ON COLUMN t_order_info.receiver_address IS '收货地址（物流模拟）';
COMMENT
ON COLUMN t_order_info.payment_type IS '支付类型：支付宝、微信';
COMMENT
ON COLUMN t_order_info.payment_app_id IS '支付应用ID';
COMMENT
ON COLUMN t_order_info.payment_channel_code IS '支付渠道编码：WXPAY、ALIPAY';
COMMENT
ON COLUMN t_order_info.version IS '乐观锁版本号';

CREATE INDEX idx_product_status_pay_type ON t_order_info (product_id, order_status, payment_type);
CREATE INDEX idx_product_payment_status_time ON t_order_info (product_id, payment_type, order_status, create_time);
CREATE INDEX idx_payment_app ON t_order_info (payment_app_id, create_time);
CREATE INDEX idx_order_lifecycle ON t_order_info (user_id, order_status, pay_status, fulfillment_status);

CREATE
OR REPLACE TRIGGER trg_order_info_uptime
BEFORE
UPDATE ON t_order_info
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_payment_info 支付流水表
-- ----------------------------
CREATE TABLE t_payment_info
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    order_no       VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(50),
    payment_type   VARCHAR(20) NOT NULL,
    trade_type     VARCHAR(20),
    trade_state    VARCHAR(50),
    payer_total    INT,
    content        CLOB,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_info PRIMARY KEY (id),
    CONSTRAINT uk_order_payment_type UNIQUE (order_no, payment_type),
    CONSTRAINT uk_transaction_id_payment_type UNIQUE (transaction_id, payment_type)
);

COMMENT
ON TABLE t_payment_info IS '支付流水表：双重幂等控制';
COMMENT
ON COLUMN t_payment_info.id IS '支付记录id';
COMMENT
ON COLUMN t_payment_info.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_payment_info.transaction_id IS '支付系统交易编号';
COMMENT
ON COLUMN t_payment_info.payment_type IS '支付类型';
COMMENT
ON COLUMN t_payment_info.trade_type IS '交易类型';
COMMENT
ON COLUMN t_payment_info.trade_state IS '交易状态';
COMMENT
ON COLUMN t_payment_info.payer_total IS '支付金额(分)';
COMMENT
ON COLUMN t_payment_info.content IS '通知参数';

CREATE INDEX idx_payment_order_no ON t_payment_info (order_no);

CREATE
OR REPLACE TRIGGER trg_payment_info_uptime
BEFORE
UPDATE ON t_payment_info
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_product 商品表
-- ----------------------------
CREATE TABLE t_product
(
    id              BIGINT IDENTITY(1, 1) NOT NULL,
    title           VARCHAR(20),
    price           INT         DEFAULT 1         NOT NULL,
    available_stock INT         DEFAULT 100       NOT NULL,
    locked_stock    INT         DEFAULT 0         NOT NULL,
    sold_stock      INT         DEFAULT 0         NOT NULL,
    lost_stock      INT         DEFAULT 0         NOT NULL,
    product_status  VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product PRIMARY KEY (id)
);

COMMENT
ON TABLE t_product IS '商品表：库存单元（本系统无独立 SKU 维度，product_id 兼作文档模型中的 sku_id）';
COMMENT
ON COLUMN t_product.id IS '商品id';
COMMENT
ON COLUMN t_product.title IS '商品名称';
COMMENT
ON COLUMN t_product.price IS '售价(分)';
COMMENT
ON COLUMN t_product.available_stock IS '可用库存：可被下单预占的数量';
COMMENT
ON COLUMN t_product.locked_stock IS '锁定库存：下单预占未结转的数量（支付后保持锁定，确认收货结转已售/退款释放）';
COMMENT
ON COLUMN t_product.sold_stock IS '已售库存：确认收货结转的数量（已售退款回补时扣减）';
COMMENT
ON COLUMN t_product.lost_stock IS '丢失/货损库存：仅退款不退货核销的数量（locked/sold 转入，货物不回仓）';
COMMENT
ON COLUMN t_product.product_status IS '商品状态：ENABLED/DISABLED';

CREATE
OR REPLACE TRIGGER trg_product_uptime
BEFORE
UPDATE ON t_product
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_refund_info 退款表
-- ----------------------------
CREATE TABLE t_refund_info
(
    id              BIGINT IDENTITY(1, 1) NOT NULL,
    order_no        VARCHAR(50) NOT NULL,
    refund_no       VARCHAR(50) NOT NULL,
    refund_id       VARCHAR(50),
    total_fee       INT,
    refund          INT,
    reason          VARCHAR(50),
    approval_status VARCHAR(20),
    approve_remark  VARCHAR(255),
    approved_time   TIMESTAMP,
    refund_status   VARCHAR(30),
    content_return  CLOB,
    content_notify  CLOB,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refund_info PRIMARY KEY (id),
    CONSTRAINT uk_refund_no UNIQUE (refund_no),
    CONSTRAINT uk_refund_id UNIQUE (refund_id)
);

COMMENT
ON TABLE t_refund_info IS '退款表：双重幂等控制';
COMMENT
ON COLUMN t_refund_info.id IS '退款单id';
COMMENT
ON COLUMN t_refund_info.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_refund_info.refund_no IS '商户退款单编号';
COMMENT
ON COLUMN t_refund_info.refund_id IS '支付系统退款单号';
COMMENT
ON COLUMN t_refund_info.total_fee IS '原订单金额(分)';
COMMENT
ON COLUMN t_refund_info.refund IS '退款金额(分)';
COMMENT
ON COLUMN t_refund_info.reason IS '退款原因';
COMMENT
ON COLUMN t_refund_info.approval_status IS '审核状态';
COMMENT
ON COLUMN t_refund_info.approve_remark IS '审核备注';
COMMENT
ON COLUMN t_refund_info.approved_time IS '审核时间';
COMMENT
ON COLUMN t_refund_info.refund_status IS '退款状态';
COMMENT
ON COLUMN t_refund_info.content_return IS '申请退款返回参数';
COMMENT
ON COLUMN t_refund_info.content_notify IS '退款结果通知参数';

CREATE INDEX idx_refund_order_approval_status ON t_refund_info (order_no, approval_status);
CREATE INDEX idx_refund_info_order_refund_status ON t_refund_info (order_no, refund_status);

CREATE
OR REPLACE TRIGGER trg_refund_info_uptime
BEFORE
UPDATE ON t_refund_info
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/


-- ----------------------------
-- t_user 用户中心
-- ----------------------------
CREATE TABLE t_user
(
    id            BIGINT IDENTITY(1, 1) NOT NULL,
    username      VARCHAR(32)                     NOT NULL,
    password_hash VARCHAR(128)                    NOT NULL,
    password_salt VARCHAR(64)                     NOT NULL,
    role          VARCHAR(32) DEFAULT 'ROLE_USER' NOT NULL,
    user_status   VARCHAR(16) DEFAULT 'ENABLED'   NOT NULL,
    create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)
);

-- 开发管理员：admin / Admin@123456
-- 首次登录后必须立即修改；生产环境建议改为部署时注入/初始化管理员。
INSERT INTO t_user (username, password_hash, password_salt, role, user_status)
VALUES ('admin', 'hb4s1XCWe4Af52ONkXsjI/oRptIrQ0lQqbpabsUHxik=', 'dev-admin-salt-2026', 'ROLE_ADMIN', 'ENABLED');

-- ----------------------------
-- t_password_reset_request 找回密码申请（V3）
-- 用户忘记密码时提交申请，管理员处理后重置为一次性随机密码
-- ----------------------------
CREATE TABLE t_password_reset_request
(
    id           BIGINT IDENTITY(1, 1) NOT NULL,
    username     VARCHAR(32)                   NOT NULL,
    remark       VARCHAR(255),
    status       VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
    admin_remark VARCHAR(255),
    handled_by   BIGINT,
    create_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_password_reset_request PRIMARY KEY (id)
);

COMMENT
ON TABLE t_password_reset_request IS '找回密码申请表：用户提交申请，管理员处理后生成一次性随机密码';
COMMENT
ON COLUMN t_password_reset_request.username IS '申请重置的登录用户名';
COMMENT
ON COLUMN t_password_reset_request.remark IS '用户申请备注（联系方式/说明）';
COMMENT
ON COLUMN t_password_reset_request.status IS '申请状态：PENDING-待处理，HANDLED-已处理，REJECTED-已拒绝';
COMMENT
ON COLUMN t_password_reset_request.admin_remark IS '管理员处理备注';
COMMENT
ON COLUMN t_password_reset_request.handled_by IS '处理的管理员用户ID';
COMMENT
ON COLUMN t_password_reset_request.create_time IS '申请时间';
COMMENT
ON COLUMN t_password_reset_request.update_time IS '更新时间';

CREATE INDEX idx_password_reset_status_time ON t_password_reset_request (status, create_time);

CREATE
OR REPLACE TRIGGER trg_password_reset_uptime
BEFORE
UPDATE ON t_password_reset_request
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_order_item 订单商品快照明细（V2：成交快照 + 退款防线字段）
-- ----------------------------
CREATE TABLE t_order_item
(
    id                    BIGINT IDENTITY(1, 1) NOT NULL,
    order_id              BIGINT              NOT NULL,
    order_no              VARCHAR(50)         NOT NULL,
    product_id            BIGINT              NOT NULL,
    product_title         VARCHAR(256),
    unit_price            INT                 NOT NULL,
    quantity              INT                 NOT NULL,
    deal_unit_amount      INT,
    original_total_amount INT,
    discount_amount       INT       DEFAULT 0 NOT NULL,
    pay_amount            INT,
    refunded_qty          INT       DEFAULT 0 NOT NULL,
    refund_frozen_qty     INT       DEFAULT 0 NOT NULL,
    refund_frozen_amount  INT       DEFAULT 0 NOT NULL,
    refunded_amount       INT       DEFAULT 0 NOT NULL,
    restocked_qty         INT       DEFAULT 0 NOT NULL,
    create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_item PRIMARY KEY (id)
);
CREATE INDEX idx_order_item_order_no ON t_order_item (order_no);
CREATE INDEX idx_order_item_product_id ON t_order_item (product_id);

COMMENT
ON TABLE t_order_item IS '订单商品明细：下单时的不可变成交快照，退款数量/金额/补库存防线的明细层';
COMMENT
ON COLUMN t_order_item.id IS '订单明细id';
COMMENT
ON COLUMN t_order_item.order_id IS '订单id';
COMMENT
ON COLUMN t_order_item.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_order_item.product_id IS '商品id（兼作库存单元/sku_id）';
COMMENT
ON COLUMN t_order_item.product_title IS '商品名称快照';
COMMENT
ON COLUMN t_order_item.unit_price IS '成交单价(分)快照';
COMMENT
ON COLUMN t_order_item.quantity IS '购买数量';
COMMENT
ON COLUMN t_order_item.deal_unit_amount IS '成交单价(分)快照（与 unit_price 一致，结构预留优惠分摊）';
COMMENT
ON COLUMN t_order_item.original_total_amount IS '原始小计(分)=unit_price*quantity';
COMMENT
ON COLUMN t_order_item.discount_amount IS '分摊优惠金额(分)，当前恒为 0，结构预留';
COMMENT
ON COLUMN t_order_item.pay_amount IS '该订单行实际承担支付金额(分)，退款资金上限';
COMMENT
ON COLUMN t_order_item.refunded_qty IS '已退款数量';
COMMENT
ON COLUMN t_order_item.refund_frozen_qty IS '退款申请冻结数量';
COMMENT
ON COLUMN t_order_item.refund_frozen_amount IS '退款申请冻结金额(分)';
COMMENT
ON COLUMN t_order_item.refunded_amount IS '已退款金额(分)';
COMMENT
ON COLUMN t_order_item.restocked_qty IS '已补回库存数量';

-- ----------------------------
-- t_refund_order / t_refund_item 退款单与退款明细（V2 资金退款主线）
-- status: APPLYING/APPROVED/REJECTED/CANCELLED/REFUNDING/SUCCESS/FAILED
-- refund_type: CANCEL_BEFORE_SHIP/RETURN_AND_REFUND/REFUND_ONLY/
--              PRICE_ADJUSTMENT/DUPLICATE_PAYMENT/LATE_PAYMENT
-- ----------------------------
CREATE TABLE t_refund_order
(
    id                  BIGINT IDENTITY(1, 1) NOT NULL,
    refund_no           VARCHAR(50)                       NOT NULL,
    order_no            VARCHAR(50)                       NOT NULL,
    user_id             BIGINT,
    refund_type         VARCHAR(32) DEFAULT 'REFUND_ONLY' NOT NULL,
    payment_no          VARCHAR(64),
    refund_amount       INT                               NOT NULL,
    reason              VARCHAR(255)                      NOT NULL,
    status              VARCHAR(32)                       NOT NULL,
    legacy_apply_status VARCHAR(20),
    apply_type          VARCHAR(20) DEFAULT 'USER'        NOT NULL,
    admin_remark        VARCHAR(255),
    goods_disposition   VARCHAR(20),
    accepted_time       TIMESTAMP,
    success_time        TIMESTAMP,
    create_time         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refund_order PRIMARY KEY (id),
    CONSTRAINT uk_refund_order_no UNIQUE (refund_no)
);
CREATE INDEX idx_refund_order_order_no ON t_refund_order (order_no);
CREATE INDEX idx_refund_order_user_id ON t_refund_order (user_id);
CREATE INDEX idx_refund_order_status ON t_refund_order (status);
CREATE INDEX idx_refund_order_status_success_time ON t_refund_order (status, success_time);

COMMENT
ON TABLE t_refund_order IS '退款单：资金退款主线，由 V1 t_refund_apply 演进；异常支付冲正（重复/晚到）也落本表但不占售后额度';
COMMENT
ON COLUMN t_refund_order.id IS '退款单id';
COMMENT
ON COLUMN t_refund_order.refund_no IS '商户退款单编号';
COMMENT
ON COLUMN t_refund_order.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_refund_order.user_id IS '申请人用户id';
COMMENT
ON COLUMN t_refund_order.refund_type IS '退款类型：CANCEL_BEFORE_SHIP-未发货取消，RETURN_AND_REFUND-退货退款，REFUND_ONLY-仅退款，PRICE_ADJUSTMENT-差价退款，DUPLICATE_PAYMENT-重复支付退款，LATE_PAYMENT-晚到支付退款';
COMMENT
ON COLUMN t_refund_order.payment_no IS '退款来源支付单编号（原路退回依据）';
COMMENT
ON COLUMN t_refund_order.refund_amount IS '退款金额(分)，服务端按订单快照计算';
COMMENT
ON COLUMN t_refund_order.reason IS '退款原因';
COMMENT
ON COLUMN t_refund_order.status IS '退款状态：APPLYING-申请中，APPROVED-已受理，REJECTED-已拒绝，CANCELLED-已撤回，REFUNDING-渠道退款中，SUCCESS-退款成功，FAILED-退款失败';
COMMENT
ON COLUMN t_refund_order.legacy_apply_status IS 'V1 旧申请状态值，仅供审计/回滚对照';
COMMENT
ON COLUMN t_refund_order.apply_type IS '申请来源：USER-用户申请，ADMIN-管理员，SYSTEM-系统自动';
COMMENT
ON COLUMN t_refund_order.admin_remark IS '管理员备注';
COMMENT
ON COLUMN t_refund_order.goods_disposition IS '已发货退款商品去向：LOST-商品丢失/无法回收，RECOVERED-商品已全部回收';
COMMENT
ON COLUMN t_refund_order.accepted_time IS '受理时间';
COMMENT
ON COLUMN t_refund_order.success_time IS '退款成功时间';

CREATE TABLE t_refund_item
(
    id                    BIGINT IDENTITY(1, 1) NOT NULL,
    refund_order_id       BIGINT              NOT NULL,
    refund_no             VARCHAR(50)         NOT NULL,
    order_item_id         BIGINT              NOT NULL,
    product_id            BIGINT              NOT NULL,
    unit_price            INT                 NOT NULL,
    refund_qty            INT                 NOT NULL,
    refund_amount         INT                 NOT NULL,
    restock_qty           INT       DEFAULT 0 NOT NULL,
    legacy_stock_returned INT       DEFAULT 0 NOT NULL,
    status                VARCHAR(32),
    create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refund_item PRIMARY KEY (id),
    CONSTRAINT uk_refund_item_order_item UNIQUE (refund_no, order_item_id)
);
CREATE INDEX idx_refund_item_order_item ON t_refund_item (order_item_id);
CREATE INDEX idx_refund_item_refund_no ON t_refund_item (refund_no);

COMMENT
ON TABLE t_refund_item IS '退款明细：退款数量/金额精确到订单行；restock_qty 记录实际补库存数量（是否补库存取决于退款类型与履约状态）';
COMMENT
ON COLUMN t_refund_item.id IS '退款明细id';
COMMENT
ON COLUMN t_refund_item.refund_order_id IS '退款单id';
COMMENT
ON COLUMN t_refund_item.refund_no IS '商户退款单编号';
COMMENT
ON COLUMN t_refund_item.order_item_id IS '订单明细id';
COMMENT
ON COLUMN t_refund_item.product_id IS '商品id';
COMMENT
ON COLUMN t_refund_item.unit_price IS '成交单价(分)快照';
COMMENT
ON COLUMN t_refund_item.refund_qty IS '本次退款数量';
COMMENT
ON COLUMN t_refund_item.refund_amount IS '本次退款金额(分)';
COMMENT
ON COLUMN t_refund_item.restock_qty IS '已补回库存数量';
COMMENT
ON COLUMN t_refund_item.legacy_stock_returned IS 'V1 旧已回补库存数，仅供审计';
COMMENT
ON COLUMN t_refund_item.status IS '明细状态（随退款单主状态流转）';

-- ----------------------------
-- t_payment_order 支付单（V2：本地订单 1:N 渠道支付尝试）
-- 一个业务订单允许多次支付尝试，但只允许一笔有效成交支付（SUCCESS）；
-- 其余成功支付进入 DUPLICATE_PAYMENT/LATE_PAYMENT 自动原路退款。
-- ----------------------------
CREATE TABLE t_payment_order
(
    id                   BIGINT IDENTITY(1, 1) NOT NULL,
    payment_no           VARCHAR(64)                   NOT NULL,
    order_no             VARCHAR(50)                   NOT NULL,
    channel              VARCHAR(32)                   NOT NULL,
    channel_order_no     VARCHAR(128),
    code_url             VARCHAR(512),
    request_amount       INT                           NOT NULL,
    paid_amount          INT         DEFAULT 0         NOT NULL,
    refund_frozen_amount INT         DEFAULT 0         NOT NULL,
    refunded_amount      INT         DEFAULT 0         NOT NULL,
    status               VARCHAR(32) DEFAULT 'CREATED' NOT NULL,
    expire_time          TIMESTAMP,
    paid_time            TIMESTAMP,
    create_time          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_order PRIMARY KEY (id),
    CONSTRAINT uk_payment_order_no UNIQUE (payment_no)
);
-- 渠道单号唯一约束（函数唯一索引）：channel_order_no 为 NULL（支付单 CREATED/PAYING 阶段未获得渠道单号）的行
-- 不参与唯一性判定；DM8 对含 NULL 列的组合唯一约束视为相等，普通 UNIQUE 会阻塞同渠道后续 NULL 支付单。
CREATE UNIQUE INDEX uk_payment_channel_order ON t_payment_order (
                                                                 CASE WHEN channel_order_no IS NULL THEN 'ID:' || CAST(id AS VARCHAR(20)) ELSE channel END,
                                                                 CASE WHEN channel_order_no IS NULL THEN NULL ELSE channel_order_no END
    );
CREATE INDEX idx_payment_order_order_no ON t_payment_order (order_no);
CREATE INDEX idx_payment_order_status ON t_payment_order (order_no, status);
CREATE INDEX idx_payment_order_channel_status_paid_time ON t_payment_order (channel, status, paid_time);

COMMENT
ON TABLE t_payment_order IS '支付单：一次支付渠道尝试；库存只属于业务订单，不属于支付单；渠道资金防线（累计退款不超过实付）落在本表';
COMMENT
ON COLUMN t_payment_order.id IS '支付单id';
COMMENT
ON COLUMN t_payment_order.payment_no IS '商户支付单编号（每次发起支付生成）';
COMMENT
ON COLUMN t_payment_order.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_payment_order.channel IS '支付渠道：WXPAY、ALIPAY';
COMMENT
ON COLUMN t_payment_order.channel_order_no IS '渠道侧交易号（微信 transaction_id / 支付宝 trade_no）';
COMMENT
ON COLUMN t_payment_order.code_url IS '支付二维码连接（NATIVE）';
COMMENT
ON COLUMN t_payment_order.request_amount IS '请求支付金额(分)';
COMMENT
ON COLUMN t_payment_order.paid_amount IS '实际支付金额(分)';
COMMENT
ON COLUMN t_payment_order.refund_frozen_amount IS '渠道退款冻结金额(分)';
COMMENT
ON COLUMN t_payment_order.refunded_amount IS '渠道累计已退款金额(分)';
COMMENT
ON COLUMN t_payment_order.status IS '支付单状态：CREATED-已创建，PAYING-支付中，SUCCESS-支付成功（有效成交），CLOSED-已关闭';
COMMENT
ON COLUMN t_payment_order.expire_time IS '支付单过期时间（必须早于等于业务订单过期时间）';
COMMENT
ON COLUMN t_payment_order.paid_time IS '支付成功时间';

-- ----------------------------
-- t_inventory_reservation 库存预占（V2：未支付阶段防超卖）
-- 状态机：LOCKED → COMMITTED（支付成功）/ RELEASED（关单/取消）
-- ----------------------------
CREATE TABLE t_inventory_reservation
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    reservation_no VARCHAR(64)                  NOT NULL,
    order_no       VARCHAR(50)                  NOT NULL,
    order_item_id  BIGINT                       NOT NULL,
    product_id     BIGINT                       NOT NULL,
    quantity       INT                          NOT NULL,
    status         VARCHAR(32) DEFAULT 'LOCKED' NOT NULL,
    expire_time    TIMESTAMP,
    commit_time    TIMESTAMP,
    release_time   TIMESTAMP,
    create_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inventory_reservation PRIMARY KEY (id),
    CONSTRAINT uk_reservation_no UNIQUE (reservation_no),
    CONSTRAINT uk_reservation_order_item UNIQUE (order_item_id)
);
CREATE INDEX idx_reservation_order_no ON t_inventory_reservation (order_no);
CREATE INDEX idx_reservation_status ON t_inventory_reservation (status, expire_time);

COMMENT
ON TABLE t_inventory_reservation IS '库存预占：每条订单明细对应一条预占（1:1），锁定→提交/释放';
COMMENT
ON COLUMN t_inventory_reservation.id IS '预占id';
COMMENT
ON COLUMN t_inventory_reservation.reservation_no IS '预占编号';
COMMENT
ON COLUMN t_inventory_reservation.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_inventory_reservation.order_item_id IS '订单明细id（1:1 唯一）';
COMMENT
ON COLUMN t_inventory_reservation.product_id IS '商品id（库存单元）';
COMMENT
ON COLUMN t_inventory_reservation.quantity IS '预占数量';
COMMENT
ON COLUMN t_inventory_reservation.status IS '预占状态：LOCKED-锁定，COMMITTED-已提交（成交），RELEASED-已释放';
COMMENT
ON COLUMN t_inventory_reservation.expire_time IS '预占过期时间（=订单过期时间）';
COMMENT
ON COLUMN t_inventory_reservation.commit_time IS '提交时间（支付成功）';
COMMENT
ON COLUMN t_inventory_reservation.release_time IS '释放时间（关单/取消）';

-- ----------------------------
-- t_inventory_transaction 库存流水（V2：审计账本 + 幂等）
-- UNIQUE(biz_no) 保证同一库存业务动作只执行一次
-- ----------------------------
CREATE TABLE t_inventory_transaction
(
    id               BIGINT IDENTITY(1, 1) NOT NULL,
    biz_no           VARCHAR(128)                  NOT NULL,
    biz_type         VARCHAR(32)                   NOT NULL,
    order_no         VARCHAR(50),
    order_item_id    BIGINT,
    refund_no        VARCHAR(50),
    product_id       BIGINT                        NOT NULL,
    available_delta  INT                           NOT NULL,
    locked_delta     INT                           NOT NULL,
    sold_delta       INT         DEFAULT 0         NOT NULL,
    lost_delta       INT         DEFAULT 0         NOT NULL,
    operation_status VARCHAR(16) DEFAULT 'SUCCESS' NOT NULL,
    error_message    VARCHAR(500),
    create_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inventory_transaction PRIMARY KEY (id),
    CONSTRAINT uk_inventory_biz_no UNIQUE (biz_no)
);
CREATE INDEX idx_inventory_tx_product ON t_inventory_transaction (product_id);
CREATE INDEX idx_inventory_tx_order_no ON t_inventory_transaction (order_no);
CREATE INDEX idx_inventory_tx_refund_no ON t_inventory_transaction (refund_no);
CREATE INDEX idx_inventory_tx_status_time ON t_inventory_transaction (operation_status, create_time);

COMMENT
ON TABLE t_inventory_transaction IS '库存流水：每次预占/提交/释放/回补/手工调整落一条，biz_no 唯一保证幂等；失败申请也记录（不改变库存）';
COMMENT
ON COLUMN t_inventory_transaction.id IS '流水id';
COMMENT
ON COLUMN t_inventory_transaction.biz_no IS '幂等业务号：TYPE:orderNo:itemId 或 REFUND_RESTOCK:refundNo:itemId 或 MANUAL_ADJUST:productId:UUID';
COMMENT
ON COLUMN t_inventory_transaction.biz_type IS '流水类型：ORDER_RESERVE-下单预占，ORDER_COMMIT-支付提交（数量不变），ORDER_SOLD-确认收货结转已售，ORDER_RELEASE-关单释放，REFUND_RESTOCK-退款回补，REFUND_LOST-仅退款货损核销，MANUAL_ADJUST-管理员手工调整';
COMMENT
ON COLUMN t_inventory_transaction.order_no IS '关联订单号';
COMMENT
ON COLUMN t_inventory_transaction.order_item_id IS '关联订单明细id';
COMMENT
ON COLUMN t_inventory_transaction.refund_no IS '关联退款单号（回补时）';
COMMENT
ON COLUMN t_inventory_transaction.product_id IS '商品id（库存单元）';
COMMENT
ON COLUMN t_inventory_transaction.available_delta IS '可用库存变化量（失败申请为 0）';
COMMENT
ON COLUMN t_inventory_transaction.locked_delta IS '锁定库存变化量';
COMMENT
ON COLUMN t_inventory_transaction.sold_delta IS '已售库存变化量';
COMMENT
ON COLUMN t_inventory_transaction.lost_delta IS '丢失/货损库存变化量（REFUND_LOST 时为正）';
COMMENT
ON COLUMN t_inventory_transaction.operation_status IS '操作状态：SUCCESS-已生效，FAILED-调整申请被拒绝（库存未变化）';
COMMENT
ON COLUMN t_inventory_transaction.error_message IS '失败原因（FAILED 时记录申请调整量与拒绝原因）';

-- ----------------------------
-- t_stock_import Excel 批量库存导入记录（V5：导入暂存 + 确认入库；V6：文件存储地址）
-- 导入仅生成 PENDING 记录，管理员在维护页选择记录、核对/编辑明细后
-- 点“确认入库”才逐条执行库存调整；实际库存变化与失败原因审计于
-- t_inventory_transaction（MANUAL_ADJUST 流水），本表仅追踪导入批次状态，
-- 并持久化 Excel 文件的存储后端与地址（LOCAL 本地磁盘 / MINIO 对象存储）。
-- ----------------------------
CREATE TABLE t_stock_import
(
    id           BIGINT IDENTITY(1, 1) NOT NULL,
    file_name    VARCHAR(255),
    item_count   INT                           NOT NULL,
    status       VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
    items_json   VARCHAR(4000)                 NOT NULL,
    storage_type VARCHAR(16) DEFAULT 'LOCAL'   NOT NULL,
    file_path    VARCHAR(512),
    create_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    confirm_time TIMESTAMP,
    CONSTRAINT pk_stock_import PRIMARY KEY (id)
);
COMMENT
ON TABLE t_stock_import IS 'Excel 批量库存导入记录：导入暂存 PENDING，确认入库后 CONFIRMED；明细 JSON 存 items_json，执行审计在 t_inventory_transaction';
COMMENT
ON COLUMN t_stock_import.file_name IS '导入的 Excel 文件名';
COMMENT
ON COLUMN t_stock_import.item_count IS '导入明细条数';
COMMENT
ON COLUMN t_stock_import.status IS 'PENDING-待确认，CONFIRMED-已入库（CAS 抢占防重复执行）';
COMMENT
ON COLUMN t_stock_import.items_json IS '导入明细 JSON：[{"productId":13,"delta":50},...]（确认时以页面编辑后明细为准执行）';
COMMENT
ON COLUMN t_stock_import.storage_type IS '文件存储后端：LOCAL-后端本地磁盘，MINIO-MinIO 对象存储（空按 LOCAL 兼容 V5）';
COMMENT
ON COLUMN t_stock_import.file_path IS '文件地址：LOCAL 为文件绝对路径，MINIO 为对象键（stock-import/{id}.xlsx）';
COMMENT
ON COLUMN t_stock_import.confirm_time IS '确认入库时间';

-- ----------------------------
-- t_order_shipment 物流单（V2：模拟物流商对接）
-- 状态机：SHIPPED → IN_TRANSIT → DELIVERED → RECEIVED（/CANCELLED）
-- ----------------------------
CREATE TABLE t_order_shipment
(
    id                BIGINT IDENTITY(1, 1) NOT NULL,
    shipment_no       VARCHAR(64)                    NOT NULL,
    order_no          VARCHAR(50)                    NOT NULL,
    logistics_company VARCHAR(64) DEFAULT '模拟快递' NOT NULL,
    tracking_no       VARCHAR(64)                    NOT NULL,
    status            VARCHAR(32) DEFAULT 'SHIPPED'  NOT NULL,
    shipped_time      TIMESTAMP,
    in_transit_time   TIMESTAMP,
    delivered_time    TIMESTAMP,
    received_time     TIMESTAMP,
    remark            VARCHAR(255),
    create_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_shipment PRIMARY KEY (id),
    CONSTRAINT uk_shipment_no UNIQUE (shipment_no),
    CONSTRAINT uk_shipment_tracking_no UNIQUE (tracking_no)
);
CREATE INDEX idx_shipment_order_no ON t_order_shipment (order_no);

COMMENT
ON TABLE t_order_shipment IS '物流单：通过 LogisticsProvider 接口对接（当前为模拟物流商），定时任务模拟运输推进，用户确认收货后履约完成';
COMMENT
ON COLUMN t_order_shipment.id IS '物流单id';
COMMENT
ON COLUMN t_order_shipment.shipment_no IS '物流单编号';
COMMENT
ON COLUMN t_order_shipment.order_no IS '商户订单编号';
COMMENT
ON COLUMN t_order_shipment.logistics_company IS '物流公司（模拟：模拟快递）';
COMMENT
ON COLUMN t_order_shipment.tracking_no IS '运单号（模拟物流商生成）';
COMMENT
ON COLUMN t_order_shipment.status IS '物流状态：SHIPPED-已发货，IN_TRANSIT-运输中，DELIVERED-已派送，RECEIVED-已收货，CANCELLED-已撤销';
COMMENT
ON COLUMN t_order_shipment.shipped_time IS '发货时间';
COMMENT
ON COLUMN t_order_shipment.in_transit_time IS '进入运输时间（模拟）';
COMMENT
ON COLUMN t_order_shipment.delivered_time IS '派送送达时间（模拟）';
COMMENT
ON COLUMN t_order_shipment.received_time IS '用户确认收货时间';
COMMENT
ON COLUMN t_order_shipment.remark IS '备注';

-- ----------------------------
-- t_bill_import 账单导入批次表（上传式对账）
-- 幂等：同渠道同账单日同账单种类唯一、同文件内容(hash)唯一，重复上传不产生新批次。
-- 账单种类：ALL（含支付+退款+撤销行）/SUCCESS（仅支付成功行）/REFUND（仅退款行），
-- 同一账单日允许一份ALL，或一份SUCCESS+一份REFUND分别对账，由服务层交叉校验。
-- ----------------------------
CREATE TABLE t_bill_import
(
    id                  BIGINT IDENTITY(1, 1) NOT NULL,
    import_no           VARCHAR(50)                    NOT NULL,
    channel_code        VARCHAR(32)                    NOT NULL,
    bill_type           VARCHAR(20) DEFAULT 'TRADE'    NOT NULL,
    bill_kind           VARCHAR(16) DEFAULT 'ALL'      NOT NULL,
    bill_date           VARCHAR(10)                    NOT NULL,
    file_name           VARCHAR(255),
    file_hash           VARCHAR(64)                    NOT NULL,
    total_record_count  INT         DEFAULT 0          NOT NULL,
    pay_record_count    INT         DEFAULT 0          NOT NULL,
    refund_record_count INT         DEFAULT 0          NOT NULL,
    bad_line_count      INT         DEFAULT 0          NOT NULL,
    matched_count       INT         DEFAULT 0          NOT NULL,
    discrepancy_count   INT         DEFAULT 0          NOT NULL,
    status              VARCHAR(20) DEFAULT 'IMPORTED' NOT NULL,
    reconcile_time      TIMESTAMP,
    error_message       VARCHAR(1000),
    create_time         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_bill_import PRIMARY KEY (id),
    CONSTRAINT uk_bill_import_no UNIQUE (import_no),
    CONSTRAINT uk_bill_import_channel_date UNIQUE (channel_code, bill_type, bill_date, bill_kind),
    CONSTRAINT uk_bill_import_file_hash UNIQUE (file_hash)
);

COMMENT
ON TABLE t_bill_import IS '账单导入批次表：管理员上传渠道账单文件的导入与对账批次，同渠道同账单日同账单种类/同文件内容唯一（幂等）';
COMMENT
ON COLUMN t_bill_import.id IS '导入批次ID';
COMMENT
ON COLUMN t_bill_import.import_no IS '导入批次业务单号';
COMMENT
ON COLUMN t_bill_import.channel_code IS '渠道编码：WXPAY（本期仅微信交易账单）';
COMMENT
ON COLUMN t_bill_import.bill_type IS '账单类型：TRADE-交易账单';
COMMENT
ON COLUMN t_bill_import.bill_kind IS '微信交易账单种类：ALL-全部账单(支付+退款)，SUCCESS-支付成功账单，REFUND-退款账单；对账范围按种类收窄';
COMMENT
ON COLUMN t_bill_import.bill_date IS '账单日期，格式yyyy-MM-dd';
COMMENT
ON COLUMN t_bill_import.file_name IS '上传的账单文件名';
COMMENT
ON COLUMN t_bill_import.file_hash IS '账单文件内容SHA-256，用于同文件重复上传幂等';
COMMENT
ON COLUMN t_bill_import.total_record_count IS '账单有效记录总笔数';
COMMENT
ON COLUMN t_bill_import.pay_record_count IS '账单支付记录笔数';
COMMENT
ON COLUMN t_bill_import.refund_record_count IS '账单退款记录笔数';
COMMENT
ON COLUMN t_bill_import.bad_line_count IS '解析失败被跳过的坏行数';
COMMENT
ON COLUMN t_bill_import.matched_count IS '对账匹配成功笔数';
COMMENT
ON COLUMN t_bill_import.discrepancy_count IS '对账差异笔数';
COMMENT
ON COLUMN t_bill_import.status IS '批次状态：IMPORTED-已导入，RECONCILED-已对账，FAILED-失败';
COMMENT
ON COLUMN t_bill_import.reconcile_time IS '对账完成时间';
COMMENT
ON COLUMN t_bill_import.error_message IS '失败原因';
COMMENT
ON COLUMN t_bill_import.create_time IS '创建时间';
COMMENT
ON COLUMN t_bill_import.update_time IS '更新时间';

CREATE INDEX idx_bill_import_bill_date ON t_bill_import (bill_date);
CREATE INDEX idx_bill_import_status ON t_bill_import (status);

CREATE
OR REPLACE TRIGGER trg_bill_import_uptime
BEFORE
UPDATE ON t_bill_import
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_bill_record 账单流水原始记录表
-- 保存账单中的支付行(PAY)与退款行(REFUND)原始数据，raw_line 保留原始行可追溯。
-- ----------------------------
CREATE TABLE t_bill_record
(
    id                  BIGINT IDENTITY(1, 1) NOT NULL,
    import_id           BIGINT      NOT NULL,
    channel_code        VARCHAR(32) NOT NULL,
    bill_date           VARCHAR(10) NOT NULL,
    record_type         VARCHAR(16) NOT NULL,
    channel_serial_no   VARCHAR(50) NOT NULL,
    biz_no              VARCHAR(50) NOT NULL,
    trade_type          VARCHAR(32),
    trade_status        VARCHAR(32),
    total_amount        INT,
    refund_amount       INT,
    trade_time          TIMESTAMP,
    refund_apply_time   TIMESTAMP,
    refund_success_time TIMESTAMP,
    raw_line            CLOB,
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_bill_record PRIMARY KEY (id),
    CONSTRAINT uk_bill_record_serial UNIQUE (channel_code, bill_date, record_type, channel_serial_no)
);

COMMENT
ON TABLE t_bill_record IS '账单流水原始记录表：账单支付行/退款行解析后的原始流水，raw_line 保留账单原文行';
COMMENT
ON COLUMN t_bill_record.id IS '流水记录ID';
COMMENT
ON COLUMN t_bill_record.import_id IS '所属账单导入批次ID';
COMMENT
ON COLUMN t_bill_record.channel_code IS '渠道编码：WXPAY';
COMMENT
ON COLUMN t_bill_record.bill_date IS '账单日期，格式yyyy-MM-dd';
COMMENT
ON COLUMN t_bill_record.record_type IS '记录类型：PAY-支付，REFUND-退款';
COMMENT
ON COLUMN t_bill_record.channel_serial_no IS '渠道流水号：支付为微信订单号，退款为微信退款单号';
COMMENT
ON COLUMN t_bill_record.biz_no IS '业务单号：支付为商户订单号，退款为商户退款单号';
COMMENT
ON COLUMN t_bill_record.trade_type IS '渠道交易类型，如JSAPI、NATIVE、REFUND';
COMMENT
ON COLUMN t_bill_record.trade_status IS '渠道交易状态，如SUCCESS';
COMMENT
ON COLUMN t_bill_record.total_amount IS '支付金额(分)，支付行取订单金额';
COMMENT
ON COLUMN t_bill_record.refund_amount IS '退款金额(分)，退款行取退款金额绝对值';
COMMENT
ON COLUMN t_bill_record.trade_time IS '交易时间';
COMMENT
ON COLUMN t_bill_record.refund_apply_time IS '退款申请时间';
COMMENT
ON COLUMN t_bill_record.refund_success_time IS '退款成功时间';
COMMENT
ON COLUMN t_bill_record.raw_line IS '账单原始行文本';
COMMENT
ON COLUMN t_bill_record.create_time IS '创建时间';
COMMENT
ON COLUMN t_bill_record.update_time IS '更新时间';

CREATE INDEX idx_bill_record_import ON t_bill_record (import_id);
CREATE INDEX idx_bill_record_biz_no ON t_bill_record (biz_no);
CREATE INDEX idx_bill_record_type_date ON t_bill_record (record_type, bill_date);

CREATE
OR REPLACE TRIGGER trg_bill_record_uptime
BEFORE
UPDATE ON t_bill_record
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_bill_reconcile_discrepancy 账单对账差异单表
-- 幂等：同批次同差异类型同业务单号唯一，重复对账不产生重复差异。
-- ----------------------------
CREATE TABLE t_bill_reconcile_discrepancy
(
    id                BIGINT IDENTITY(1, 1) NOT NULL,
    import_id         BIGINT                     NOT NULL,
    bill_date         VARCHAR(10)                NOT NULL,
    biz_type          VARCHAR(16)                NOT NULL,
    discrepancy_type  VARCHAR(40)                NOT NULL,
    biz_no            VARCHAR(50),
    channel_serial_no VARCHAR(50),
    local_biz_no      VARCHAR(50),
    local_ledger_no   VARCHAR(64),
    local_serial_no   VARCHAR(64),
    channel_amount    INT,
    local_amount      INT,
    channel_status    VARCHAR(50),
    local_status      VARCHAR(50),
    status            VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
    resolve_remark    VARCHAR(512),
    resolved_time     TIMESTAMP,
    resolved_by       VARCHAR(64),
    create_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_bill_reconcile_discrepancy PRIMARY KEY (id),
    CONSTRAINT uk_bill_discrepancy_biz UNIQUE (import_id, discrepancy_type, biz_type, biz_no)
);

COMMENT
ON TABLE t_bill_reconcile_discrepancy IS '账账核对差异单：渠道交易账与平台PaymentOrder/RefundOrder账本逐笔核对产生的差异，同批次同类型同业务单号唯一（幂等）';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.id IS '差异单ID';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.import_id IS '所属账单导入批次ID';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.bill_date IS '账单日期，格式yyyy-MM-dd';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.biz_type IS '业务类型：PAY-支付核对，REFUND-退款核对';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.discrepancy_type IS '账账差异：单边账、金额/状态不一致、渠道流水/业务单号不一致、重复流水';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.biz_no IS '业务单号：商户订单号或商户退款单号';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.channel_serial_no IS '渠道流水号：微信订单号/退款单号';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.local_biz_no IS '平台侧业务单号：支付为订单号，退款为退款单号';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.local_ledger_no IS '平台账本单号：支付为payment_no，退款为refund_no';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.local_serial_no IS '平台记录的渠道流水号：支付为channel_order_no；退款当前可为空';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.channel_amount IS '渠道侧金额(分)';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.local_amount IS '本地侧金额(分)';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.channel_status IS '渠道侧状态';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.local_status IS '本地侧状态';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.status IS '处理状态：OPEN-待处理，RESOLVED-已处理';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.resolve_remark IS '处理备注';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.resolved_time IS '处理时间';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.resolved_by IS '处理人';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.create_time IS '创建时间';
COMMENT
ON COLUMN t_bill_reconcile_discrepancy.update_time IS '更新时间';

CREATE INDEX idx_bill_discrepancy_import ON t_bill_reconcile_discrepancy (import_id);
CREATE INDEX idx_bill_discrepancy_status ON t_bill_reconcile_discrepancy (status);
CREATE INDEX idx_bill_discrepancy_bill_date ON t_bill_reconcile_discrepancy (bill_date);

CREATE
OR REPLACE TRIGGER trg_bill_reconcile_discrepancy_uptime
BEFORE
UPDATE ON t_bill_reconcile_discrepancy
    FOR EACH ROW
BEGIN
  :NEW
.update_time := CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- Initial data：来源于现有 wxpay.properties / alipay-sandbox.properties，不使用 mock 数据
-- DELETE 清理守卫确保幂等：即使 DROP 未生效也不会违反唯一性约束。
-- ----------------------------
DELETE
FROM t_payment_app
WHERE app_code IN ('WXPAY_DEFAULT', 'ALIPAY_SANDBOX_DEFAULT');
DELETE
FROM t_payment_channel
WHERE channel_code IN ('WXPAY', 'ALIPAY');
DELETE
FROM t_product
WHERE title IN ('Java课程', '大数据课程', '前端课程', 'UI课程');

INSERT INTO t_payment_channel (channel_name, channel_code, channel_status, channel_desc, config_params, appid, mch_id,
                               mch_serial_no, private_key, api_v3_key, partner_key, sort_order, create_time,
                               update_time)
VALUES ('微信支付', 'WXPAY', 'ENABLED', '微信支付渠道公共配置与商户信息',
        '{"domain":"https://api.mch.weixin.qq.com","notifyUrl":"https://5834-2409-8a50-3e25-e7f0-ecc6-e42-7b4c-1466.ngrok-free.app"}',
        'wx74862e0dfcf69954', '1558950191', '34345964330B66427E0D3D28826C4993C77E631F',
        '-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDnSAKI8sea8p+d\nOBVPWlZmxqJfPbdhzZxdI5Kx1j5SJNZwXWtr43/giw38pwzSlBI+bubBcYlkFTI0\nguigMZO/yueb1mZChaY/JG1vsT02Ubj0xkVvBwKNbYS48NEpZhK61Mia09R4n1iH\n1vip9kt8J6Zrx+xIqwmuCNWigyivGrvY9AdevCNlNSVdHVOZUJiJ6UGtvVmgZb0u\nRTwBzfkjnwTgEcsrZMmF15nFubFsyJLyF/zY4NhrISc8H/rbjgleqa8ybYL26iTS\ngfPCXe4U9f8fNFF2bSA06GTiB2R93q2B0zHeUYrpgF4XOGlIAqH+Ea4Vn+aOj6I0\npduh03idAgMBAAECggEBAJ+4SB/hYd1szrPZhkXtwhtp87pIObtuLhzYMzdjGFjM\nHdctfMDeNHKSNU+U4bMPFOZO2kcfLF2Ukb5X5WSzuDBMZNRnJOmtuJiEhJsM0JQR\nreREhLDfK3EWAAFkNV4corSpu/vIbEP87zuoRsPBVnHgQ/rM7y1kCORKL5bycwcw\n5BI4xhULKAu14LEcDL3+xDJo39w+WCFlxuP+6Bs7+vIeavs+AC3TJkA4kg2nyWd3\nW07xPjHl64f17icqsFhuFZ+VuSf5CAgQGWDbC7BHqRkDStUDSiiUiFushouKCLdK\nMpA0x4ogb2ZwfZDRhZHiLNAGe4QovYCcXWBydzuT0WECgYEA828Bo1JAHE5kdnsO\nE9+enH/yMcOKTRnuYPiXsFXNvqofc5tZiXJmVE/+EKv7LFmtUA6qqKC7FDek8TpP\nSkfXmSDAgfM6AdzT0YoHH23FRVewnFMEYumtogXsXJTyI5siBSJp16s9Rn/YwESt\nJqjW5+9Ck1dkU+UJCZ4lOw4HeGkCgYEA8zho2BKQTh3P/xcFcoTcunVZpRayVkHM\ng8Ef6RGGo4vM1oshQLvXyPqCmhAIf6j71I9WPqUwjmeGyaR7Hir0dbgTCm2fJPFW\nlxAvgbCISxEPz10RYBcR2umMSlJLfZfhqv1CyfU4vfCTbdOimgsz2039E3oLTbzg\neDe/mdzu2BUCgYEAleKjf4wFLWiXMtxRrqrhXjrpRPrBDPgKbmqh+1DZfawB8YyV\ndKublg4qwNkjrgsJS2G8cleE2M3qIR1l9LaHaSFhZqH79WmigkIaYJ+V9zwm4hm7\neaun3TsIbXjIHmRGbiLiSIiHEgFl0/x1IHiU2fnXZCFLBNzg06ssAVCCCQECgYA1\n4BfxTONkOlxZgAr33BBcySPLWuS0EK0xvjTIVtaBIbWFDJqYEUPyQ/NsFwMa7B6k\nbf/HrqW71ZjYz7Np8k/mR5kIJVIsR71Lhw1O6AC4yBW9dDsmEtYkrLkjuWj5cAxP\n6PvDaqtf/4tYt5l8D+Ezwem+R7l7RcxfNNIfTf4mJQKBgE57dnRx+Ijx7VHjJvjl\nX2jB/VSVGpK5OADykmmZ/wvHPlQcyzd+5kAIoJhSuY48CFeI1DOogR2p01LEFQEL\nj4AI5FqOOQwRJvNmfoKcKwO36tSxSEGSM8POKOsa21PG/gvDpJjVFo2hn5QcMHWn\nz5SjsgA/1YbXejubdLxT/3pl\n-----END PRIVATE KEY-----',
        'UDuLFDcmy5Eb6o0nTNZdu6ek4DDh4K8B', 'T6m9iK73b0kn9g5v426MKfHQH7X8rKwb', 10, CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP);

INSERT INTO t_payment_channel (channel_name, channel_code, channel_status, channel_desc, config_params, alipay_app_id,
                               seller_id, merchant_private_key, alipay_public_key, sort_order, create_time, update_time)
VALUES ('支付宝', 'ALIPAY', 'ENABLED', '支付宝渠道公共配置与商户信息',
        '{"gatewayUrl":"https://openapi-sandbox.dl.alipaydev.com/gateway.do","contentKey":"GD8AS9VQy0hZROhMzOARQw==","returnUrl":"http://localhost:8083/#/success","notifyUrl":"http://arhi.nat300.top/api/ali-pay/trade/notify"}',
        '9021000136667568', '2088721034748965',
        'MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCCDhLjo3lEFNcWdMnehWhHPams3KbmxpVJTxmIglvMLt6j207pSlWxSvacT3wFebXW4herg9RWhjTTh7KFwkxRWwzBVfp63YvEUbNsF09X1K6NqT2Kqz/w77l38lhQxpVau8odV3nHR+KhtDD2YJ9z8C2AzJLzKCD5CdK5coP7k+mRx3/mlOEj07oA7H6CrDkm4j4oasqEeLfbyJrXYcpPMi2hIHMXkR21fX5905v9BxwPhkVFsxHOABPYMWu5+uPpjRH1e/HKZpo5sinud05oWI0b1waMRZ9AGUEJnNZ3Yg9b9b2k1ycnCkApzy8cgQOgGhr6Rr9V7ieAsOn0YahLAgMBAAECggEAeImcvjj0GsKJ+zkxJDlXRbgD+7/iPL/O+0wBqUDQ3fSOyyVnBNetho2o9YTBuL1uaIPSVlfvxGXMrkT1k/1aCIkv0Dzk011kvgbPGZ6dHhVz1r4F2PERaTh2GJKXgf4bzSWBlSJPLwEULrU4MBGrl6QCOH7ir9UAgnC1SsW1R8QkCbgXchZkh9s3LddJ0Skb9Qu144yhT3tSxIMDL/ZJqANIR0Smv9tUuP2+DehqBH8OEapW4c00mt7OWT0jQ8H/8BaFV3j0q8SCn3cmKBFeUzh9H2UlHjiVUeF9mRmiEJUK8/N/li/WZbiWwRXOcRlno54+aRbvlwo+A8gWZF2n+QKBgQC6EIONzns/n0wSB87X0ZWUb7Y3Q6aReu/JvFwNuUA1wbS4bw7PTAijvB0daLXke142ujt6tnghyQHQuQU+5xzA5q8Q3Ooy8OZo9YgCRzNmU20CltEWcWijOt7ndCxpEVDeB7dY55yavVI8hgWX5LFI0t4Zwa8u+c+QL9C48DUVNQKBgQCy8DbydcZdRx2XSYZGtd0p+yR6lFucxpIFIYt/CFXcKGLKasF7Yhds5RKderp/I/1WZl0kwpbTv17HuvJqdoDX4qLXhvlh30P9Pbvvk7YhIv9oR9NqRtTTr0E40jALTouAuEaWs1f49C6AfrWJC26jgNHWpdeEkUQ6NN6DaP73fwKBgAsDRTYUfZkDbbY3fheqEQdrIUbeGzLLKvwuyOgLCfDkmTS9ZgwA/RXr4XFHLFTstGPa3ABkYnHletUGznetqDcGsF/4I2iGd6zIs5cm7bTlxTL9CD0i00WuC1l5t9M0MiwiGskJVGyYPhDVAem+oHul931gyGSoZo+rNNhtZ0btAoGAU2gM9K9ZKxl+/YnUARm8YVkjA9Arc8RLRAEC2M+11c0tX1Sroytx59xO9QDD9Yd9CszkFcJuM308XLUTUfSy0e5eIUBU9f3v3xbrhxy/BGsfyifQr/UcNx+1sxqmMl8GP5WlsZEfLHgFRPfK/npJtATTys26y5w6xTbnkTFbx1kCgYB+2wDnTWavXJkQErniXh3ifmC7qLcBNBpSUrI9eAtI4AwqKfBnp7d+wGuh05epecb1gzE76bWPqgIX7N5zmpZaQ/qZL+CgcGpU/7PN/qKGrqa1JEu8ZMaDhFys6MkIpQIGMElgnbM0cXvNrY8ReKvcGGysdtLnIBrp9BXcm+uWSg==',
        'MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAh9ivuRV+2eNbAzxXQ3bmx3wdhBHVaNQlhzv6wDXBMNhKG/AJDOAtYNbzPYJJPfySOrxyMlcTCvTGMS//Zn4yJHXx8IjJ4LSIguyy2BjDMwvDlF+TP7Xj6F/qtHWzuhztDnkLdAOwUx70Zyq1ajjzU5b2ku3J9WX5bwmnDpFGCVCwwG51mJYTd7Fwr4nE1qRZeMgGMhR7xR5Qdu1Nwx8Z+l1FCs47eZiirearT83/pwCRPD364SHq2uBJLtse9ozO7meBb8mzt6CDZKK6imEX1MKeVILbfE7GZAbtIAV4TLbvhB4VZuVxQrYmppPGhMpEiFDkpk0nFyNb7tz3HcHIQwIDAQAB',
        20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 使用子查询获取渠道ID，避免硬编码 channel_id=1/2 在 IDENTITY 未重置时失效
-- 应用表不再保存商户参数（app_config 已移除），商户信息统一在渠道表
INSERT INTO t_payment_app (app_name, app_code, app_status, channel_id, app_desc, sort_order, create_time, update_time)
VALUES ('微信支付默认应用', 'WXPAY_DEFAULT', 'ENABLED', (SELECT id FROM t_payment_channel WHERE channel_code = 'WXPAY'),
        '微信支付默认应用（商户参数见WXPAY渠道）', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_payment_app (app_name, app_code, app_status, channel_id, app_desc, sort_order, create_time, update_time)
VALUES ('支付宝沙箱默认应用', 'ALIPAY_SANDBOX_DEFAULT', 'ENABLED',
        (SELECT id FROM t_payment_channel WHERE channel_code = 'ALIPAY'), '支付宝沙箱默认应用（商户参数见ALIPAY渠道）',
        20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time)
VALUES ('Java课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');
INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time)
VALUES ('大数据课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');
INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time)
VALUES ('前端课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');
INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time)
VALUES ('UI课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');

-- =====================================================================
-- 本地消息表（事务性发件箱）：业务事务内落库 PENDING，事务提交后投递 MQ；
-- 发送者确认到达置 SENT；消费者监听器成功返回后回写 CONSUMED；重试超限置 FAILED 待人工补偿
-- =====================================================================
CREATE TABLE t_local_message
(
    id              BIGINT IDENTITY(1, 1) NOT NULL,
    biz_type        VARCHAR(32)                   NOT NULL,
    biz_no          VARCHAR(64)                   NOT NULL,
    message_content CLOB                          NOT NULL,
    status          VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
    retry_count     INT         DEFAULT 0         NOT NULL,
    next_retry_time TIMESTAMP                     NOT NULL,
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_local_message PRIMARY KEY (id)
);
CREATE INDEX idx_local_message_scan ON t_local_message (status, next_retry_time);
CREATE INDEX idx_local_message_biz ON t_local_message (biz_type, biz_no);
COMMENT
ON TABLE t_local_message IS '本地消息表（事务性发件箱）：延迟关单/退款同步消息可靠投递与消费回写';
COMMENT
ON COLUMN t_local_message.biz_type IS '业务类型：ORDER_CLOSE-延迟关单，REFUND_SYNC-退款状态同步';
COMMENT
ON COLUMN t_local_message.biz_no IS '业务单号：orderNo/refundNo';
COMMENT
ON COLUMN t_local_message.message_content IS '消息内容JSON（与MQ消息体一致）';
COMMENT
ON COLUMN t_local_message.status IS '状态：PENDING-待投递，SENT-已投递(发送者确认)，CONSUMED-已消费(消费成功回写)，FAILED-投递失败待人工补偿';
COMMENT
ON COLUMN t_local_message.retry_count IS '投递重试次数';
COMMENT
ON COLUMN t_local_message.next_retry_time IS '下次重试时间';

COMMIT;

-- ===============================================================
-- PART 2  省市区基础数据（t_region + 全国三级行政区划数据）
-- ===============================================================

DROP TABLE IF EXISTS t_region CASCADE;
-- 省市区基础数据表：三级区域树（公共只读）
CREATE TABLE t_region
(
    id        BIGINT IDENTITY(1, 1) NOT NULL,
    parent_id BIGINT      NOT NULL DEFAULT 0,
    code      VARCHAR(16) NOT NULL,
    name      VARCHAR(32) NOT NULL,
    level     INT         NOT NULL,
    sort      INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_region PRIMARY KEY (id)
);
CREATE INDEX idx_region_parent ON t_region (parent_id);
CREATE INDEX idx_region_code ON t_region (code);

-- ---------- 全国三级行政区划数据（省/市/区县） ----------
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '11', '北京市', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1, '1101', '市辖区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110101', '东城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110102', '西城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110105', '朝阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110106', '丰台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110107', '石景山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110108', '海淀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110109', '门头沟区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110111', '房山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110112', '通州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110113', '顺义区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110114', '昌平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110115', '大兴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110116', '怀柔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110117', '平谷区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110118', '密云区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2, '110119', '延庆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '12', '天津市', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (19, '1201', '市辖区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120101', '和平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120102', '河东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120103', '河西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120104', '南开区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120105', '河北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120106', '红桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120110', '东丽区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120111', '西青区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120112', '津南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120113', '北辰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120114', '武清区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120115', '宝坻区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120116', '滨海新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120117', '宁河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120118', '静海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (20, '120119', '蓟州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '13', '河北省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1301', '石家庄市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130102', '长安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130104', '桥西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130105', '新华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130107', '井陉矿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130108', '裕华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130109', '藁城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130110', '鹿泉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130111', '栾城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130121', '井陉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130123', '正定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130125', '行唐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130126', '灵寿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130127', '高邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130128', '深泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130129', '赞皇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130130', '无极县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130131', '平山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130132', '元氏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130133', '赵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130171', '石家庄高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130172', '石家庄循环化工园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130181', '辛集市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130183', '晋州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (38, '130184', '新乐市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1302', '唐山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130202', '路南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130203', '路北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130204', '古冶区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130205', '开平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130207', '丰南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130208', '丰润区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130209', '曹妃甸区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130224', '滦南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130225', '乐亭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130227', '迁西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130229', '玉田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130271', '河北唐山芦台经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130272', '唐山市汉沽管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130273', '唐山高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130274', '河北唐山海港经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130281', '遵化市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130283', '迁安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (63, '130284', '滦州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1303', '秦皇岛市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130302', '海港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130303', '山海关区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130304', '北戴河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130306', '抚宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130321', '青龙满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130322', '昌黎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130324', '卢龙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130371', '秦皇岛市经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (82, '130372', '北戴河新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1304', '邯郸市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130402', '邯山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130403', '丛台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130404', '复兴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130406', '峰峰矿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130407', '肥乡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130408', '永年区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130423', '临漳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130424', '成安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130425', '大名县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130426', '涉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130427', '磁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130430', '邱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130431', '鸡泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130432', '广平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130433', '馆陶县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130434', '魏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130435', '曲周县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130471', '邯郸经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130473', '邯郸冀南新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (92, '130481', '武安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1305', '邢台市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130502', '襄都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130503', '信都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130505', '任泽区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130506', '南和区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130522', '临城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130523', '内丘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130524', '柏乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130525', '隆尧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130528', '宁晋县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130529', '巨鹿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130530', '新河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130531', '广宗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130532', '平乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130533', '威县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130534', '清河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130535', '临西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130571', '河北邢台经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130581', '南宫市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (113, '130582', '沙河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1306', '保定市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130602', '竞秀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130606', '莲池区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130607', '满城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130608', '清苑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130609', '徐水区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130623', '涞水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130624', '阜平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130626', '定兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130627', '唐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130628', '高阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130629', '容城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130630', '涞源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130631', '望都县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130632', '安新县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130633', '易县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130634', '曲阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130635', '蠡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130636', '顺平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130637', '博野县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130638', '雄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130671', '保定高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130672', '保定白沟新城', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130681', '涿州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130682', '定州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130683', '安国市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (133, '130684', '高碑店市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1307', '张家口市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130702', '桥东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130703', '桥西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130705', '宣化区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130706', '下花园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130708', '万全区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130709', '崇礼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130722', '张北县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130723', '康保县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130724', '沽源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130725', '尚义县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130726', '蔚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130727', '阳原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130728', '怀安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130730', '怀来县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130731', '涿鹿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130732', '赤城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130771', '张家口经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130772', '张家口市察北管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (160, '130773', '张家口市塞北管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1308', '承德市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130802', '双桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130803', '双滦区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130804', '鹰手营子矿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130821', '承德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130822', '兴隆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130824', '滦平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130825', '隆化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130826', '丰宁满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130827', '宽城满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130828', '围场满族蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130871', '承德高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (180, '130881', '平泉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1309', '沧州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130902', '新华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130903', '运河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130921', '沧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130922', '青县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130923', '东光县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130924', '海兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130925', '盐山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130926', '肃宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130927', '南皮县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130928', '吴桥县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130929', '献县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130930', '孟村回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130971', '河北沧州经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130972', '沧州高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130973', '沧州渤海新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130981', '泊头市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130982', '任丘市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130983', '黄骅市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (193, '130984', '河间市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1310', '廊坊市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131002', '安次区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131003', '广阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131022', '固安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131023', '永清县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131024', '香河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131025', '大城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131026', '文安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131028', '大厂回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131071', '廊坊经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131081', '霸州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (213, '131082', '三河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (37, '1311', '衡水市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131102', '桃城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131103', '冀州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131121', '枣强县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131122', '武邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131123', '武强县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131124', '饶阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131125', '安平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131126', '故城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131127', '景县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131128', '阜城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131171', '河北衡水高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131172', '衡水滨湖新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (225, '131182', '深州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '14', '山西省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1401', '太原市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140105', '小店区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140106', '迎泽区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140107', '杏花岭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140108', '尖草坪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140109', '万柏林区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140110', '晋源区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140121', '清徐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140122', '阳曲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140123', '娄烦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140171', '山西转型综合改革示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (240, '140181', '古交市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1402', '大同市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140212', '新荣区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140213', '平城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140214', '云冈区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140215', '云州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140221', '阳高县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140222', '天镇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140223', '广灵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140224', '灵丘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140225', '浑源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140226', '左云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (252, '140271', '山西大同经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1403', '阳泉市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (264, '140302', '城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (264, '140303', '矿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (264, '140311', '郊区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (264, '140321', '平定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (264, '140322', '盂县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1404', '长治市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140403', '潞州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140404', '上党区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140405', '屯留区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140406', '潞城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140423', '襄垣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140425', '平顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140426', '黎城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140427', '壶关县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140428', '长子县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140429', '武乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140430', '沁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (270, '140431', '沁源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1405', '晋城市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (283, '140502', '城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (283, '140521', '沁水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (283, '140522', '阳城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (283, '140524', '陵川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (283, '140525', '泽州县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (283, '140581', '高平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1406', '朔州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140602', '朔城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140603', '平鲁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140621', '山阴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140622', '应县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140623', '右玉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140671', '山西朔州经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (290, '140681', '怀仁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1407', '晋中市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140702', '榆次区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140703', '太谷区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140721', '榆社县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140722', '左权县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140723', '和顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140724', '昔阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140725', '寿阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140727', '祁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140728', '平遥县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140729', '灵石县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (298, '140781', '介休市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1408', '运城市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140802', '盐湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140821', '临猗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140822', '万荣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140823', '闻喜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140824', '稷山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140825', '新绛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140826', '绛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140827', '垣曲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140828', '夏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140829', '平陆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140830', '芮城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140881', '永济市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (310, '140882', '河津市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1409', '忻州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140902', '忻府区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140921', '定襄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140922', '五台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140923', '代县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140924', '繁峙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140925', '宁武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140926', '静乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140927', '神池县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140928', '五寨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140929', '岢岚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140930', '河曲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140931', '保德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140932', '偏关县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140971', '五台山风景名胜区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (324, '140981', '原平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1410', '临汾市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141002', '尧都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141021', '曲沃县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141022', '翼城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141023', '襄汾县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141024', '洪洞县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141025', '古县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141026', '安泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141027', '浮山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141028', '吉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141029', '乡宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141030', '大宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141031', '隰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141032', '永和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141033', '蒲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141034', '汾西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141081', '侯马市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (340, '141082', '霍州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (239, '1411', '吕梁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141102', '离石区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141121', '文水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141122', '交城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141123', '兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141124', '临县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141125', '柳林县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141126', '石楼县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141127', '岚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141128', '方山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141129', '中阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141130', '交口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141181', '孝义市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (358, '141182', '汾阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '15', '内蒙古自治区', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1501', '呼和浩特市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150102', '新城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150103', '回民区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150104', '玉泉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150105', '赛罕区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150121', '土默特左旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150122', '托克托县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150123', '和林格尔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150124', '清水河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150125', '武川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (373, '150172', '呼和浩特经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1502', '包头市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150202', '东河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150203', '昆都仑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150204', '青山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150205', '石拐区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150206', '白云鄂博矿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150207', '九原区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150221', '土默特右旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150222', '固阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150223', '达尔罕茂明安联合旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (384, '150271', '包头稀土高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1503', '乌海市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (395, '150302', '海勃湾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (395, '150303', '海南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (395, '150304', '乌达区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1504', '赤峰市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150402', '红山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150403', '元宝山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150404', '松山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150421', '阿鲁科尔沁旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150422', '巴林左旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150423', '巴林右旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150424', '林西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150425', '克什克腾旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150426', '翁牛特旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150428', '喀喇沁旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150429', '宁城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (399, '150430', '敖汉旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1505', '通辽市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150502', '科尔沁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150521', '科尔沁左翼中旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150522', '科尔沁左翼后旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150523', '开鲁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150524', '库伦旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150525', '奈曼旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150526', '扎鲁特旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150571', '通辽经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (412, '150581', '霍林郭勒市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1506', '鄂尔多斯市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150602', '东胜区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150603', '康巴什区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150621', '达拉特旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150622', '准格尔旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150623', '鄂托克前旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150624', '鄂托克旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150625', '杭锦旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150626', '乌审旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (422, '150627', '伊金霍洛旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1507', '呼伦贝尔市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150702', '海拉尔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150703', '扎赉诺尔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150721', '阿荣旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150722', '莫力达瓦达斡尔族自治旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150723', '鄂伦春自治旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150724', '鄂温克族自治旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150725', '陈巴尔虎旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150726', '新巴尔虎左旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150727', '新巴尔虎右旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150781', '满洲里市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150782', '牙克石市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150783', '扎兰屯市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150784', '额尔古纳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (432, '150785', '根河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1508', '巴彦淖尔市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150802', '临河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150821', '五原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150822', '磴口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150823', '乌拉特前旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150824', '乌拉特中旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150825', '乌拉特后旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (447, '150826', '杭锦后旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1509', '乌兰察布市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150902', '集宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150921', '卓资县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150922', '化德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150923', '商都县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150924', '兴和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150925', '凉城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150926', '察哈尔右翼前旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150927', '察哈尔右翼中旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150928', '察哈尔右翼后旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150929', '四子王旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (455, '150981', '丰镇市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1522', '兴安盟', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (467, '152201', '乌兰浩特市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (467, '152202', '阿尔山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (467, '152221', '科尔沁右翼前旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (467, '152222', '科尔沁右翼中旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (467, '152223', '扎赉特旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (467, '152224', '突泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1525', '锡林郭勒盟', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152501', '二连浩特市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152502', '锡林浩特市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152522', '阿巴嘎旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152523', '苏尼特左旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152524', '苏尼特右旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152525', '东乌珠穆沁旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152526', '西乌珠穆沁旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152527', '太仆寺旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152528', '镶黄旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152529', '正镶白旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152530', '正蓝旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152531', '多伦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (474, '152571', '乌拉盖管理区管委会', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (372, '1529', '阿拉善盟', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (488, '152921', '阿拉善左旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (488, '152922', '阿拉善右旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (488, '152923', '额济纳旗', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (488, '152971', '内蒙古阿拉善高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '21', '辽宁省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2101', '沈阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210102', '和平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210103', '沈河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210104', '大东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210105', '皇姑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210106', '铁西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210111', '苏家屯区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210112', '浑南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210113', '沈北新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210114', '于洪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210115', '辽中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210123', '康平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210124', '法库县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (494, '210181', '新民市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2102', '大连市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210202', '中山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210203', '西岗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210204', '沙河口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210211', '甘井子区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210212', '旅顺口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210213', '金州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210214', '普兰店区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210224', '长海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210281', '瓦房店市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (508, '210283', '庄河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2103', '鞍山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210302', '铁东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210303', '铁西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210304', '立山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210311', '千山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210321', '台安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210323', '岫岩满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (519, '210381', '海城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2104', '抚顺市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210402', '新抚区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210403', '东洲区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210404', '望花区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210411', '顺城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210421', '抚顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210422', '新宾满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (527, '210423', '清原满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2105', '本溪市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (535, '210502', '平山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (535, '210503', '溪湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (535, '210504', '明山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (535, '210505', '南芬区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (535, '210521', '本溪满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (535, '210522', '桓仁满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2106', '丹东市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (542, '210602', '元宝区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (542, '210603', '振兴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (542, '210604', '振安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (542, '210624', '宽甸满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (542, '210681', '东港市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (542, '210682', '凤城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2107', '锦州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210702', '古塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210703', '凌河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210711', '太和区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210726', '黑山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210727', '义县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210781', '凌海市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (549, '210782', '北镇市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2108', '营口市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (557, '210802', '站前区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (557, '210803', '西市区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (557, '210804', '鲅鱼圈区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (557, '210811', '老边区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (557, '210881', '盖州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (557, '210882', '大石桥市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2109', '阜新市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210902', '海州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210903', '新邱区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210904', '太平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210905', '清河门区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210911', '细河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210921', '阜新蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (564, '210922', '彰武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2110', '辽阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211002', '白塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211003', '文圣区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211004', '宏伟区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211005', '弓长岭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211011', '太子河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211021', '辽阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (572, '211081', '灯塔市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2111', '盘锦市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (580, '211102', '双台子区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (580, '211103', '兴隆台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (580, '211104', '大洼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (580, '211122', '盘山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2112', '铁岭市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211202', '银州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211204', '清河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211221', '铁岭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211223', '西丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211224', '昌图县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211281', '调兵山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (585, '211282', '开原市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2113', '朝阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211302', '双塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211303', '龙城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211321', '朝阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211322', '建平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211324', '喀喇沁左翼蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211381', '北票市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (593, '211382', '凌源市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (493, '2114', '葫芦岛市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (601, '211402', '连山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (601, '211403', '龙港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (601, '211404', '南票区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (601, '211421', '绥中县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (601, '211422', '建昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (601, '211481', '兴城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '22', '吉林省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2201', '长春市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220102', '南关区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220103', '宽城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220104', '朝阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220105', '二道区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220106', '绿园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220112', '双阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220113', '九台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220122', '农安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220171', '长春经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220172', '长春净月高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220173', '长春高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220174', '长春汽车经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220182', '榆树市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220183', '德惠市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (609, '220184', '公主岭市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2202', '吉林市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220202', '昌邑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220203', '龙潭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220204', '船营区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220211', '丰满区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220221', '永吉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220271', '吉林经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220272', '吉林高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220273', '吉林中国新加坡食品区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220281', '蛟河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220282', '桦甸市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220283', '舒兰市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (625, '220284', '磐石市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2203', '四平市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (638, '220302', '铁西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (638, '220303', '铁东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (638, '220322', '梨树县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (638, '220323', '伊通满族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (638, '220382', '双辽市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2204', '辽源市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (644, '220402', '龙山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (644, '220403', '西安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (644, '220421', '东丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (644, '220422', '东辽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2205', '通化市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220502', '东昌区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220503', '二道江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220521', '通化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220523', '辉南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220524', '柳河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220581', '梅河口市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (649, '220582', '集安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2206', '白山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (657, '220602', '浑江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (657, '220605', '江源区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (657, '220621', '抚松县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (657, '220622', '靖宇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (657, '220623', '长白朝鲜族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (657, '220681', '临江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2207', '松原市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (664, '220702', '宁江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (664, '220721', '前郭尔罗斯蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (664, '220722', '长岭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (664, '220723', '乾安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (664, '220771', '吉林松原经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (664, '220781', '扶余市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2208', '白城市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (671, '220802', '洮北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (671, '220821', '镇赉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (671, '220822', '通榆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (671, '220871', '吉林白城经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (671, '220881', '洮南市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (671, '220882', '大安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (608, '2224', '延边朝鲜族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222401', '延吉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222402', '图们市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222403', '敦化市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222404', '珲春市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222405', '龙井市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222406', '和龙市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222424', '汪清县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (678, '222426', '安图县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '23', '黑龙江省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2301', '哈尔滨市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230102', '道里区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230103', '南岗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230104', '道外区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230108', '平房区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230109', '松北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230110', '香坊区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230111', '呼兰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230112', '阿城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230113', '双城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230123', '依兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230124', '方正县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230125', '宾县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230126', '巴彦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230127', '木兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230128', '通河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230129', '延寿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230183', '尚志市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (688, '230184', '五常市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2302', '齐齐哈尔市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230202', '龙沙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230203', '建华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230204', '铁锋区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230205', '昂昂溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230206', '富拉尔基区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230207', '碾子山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230208', '梅里斯达斡尔族区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230221', '龙江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230223', '依安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230224', '泰来县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230225', '甘南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230227', '富裕县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230229', '克山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230230', '克东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230231', '拜泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (707, '230281', '讷河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2303', '鸡西市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230302', '鸡冠区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230303', '恒山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230304', '滴道区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230305', '梨树区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230306', '城子河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230307', '麻山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230321', '鸡东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230381', '虎林市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (724, '230382', '密山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2304', '鹤岗市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230402', '向阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230403', '工农区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230404', '南山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230405', '兴安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230406', '东山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230407', '兴山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230421', '萝北县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (734, '230422', '绥滨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2305', '双鸭山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230502', '尖山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230503', '岭东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230505', '四方台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230506', '宝山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230521', '集贤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230522', '友谊县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230523', '宝清县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (743, '230524', '饶河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2306', '大庆市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230602', '萨尔图区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230603', '龙凤区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230604', '让胡路区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230605', '红岗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230606', '大同区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230621', '肇州县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230622', '肇源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230623', '林甸县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230624', '杜尔伯特蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (752, '230671', '大庆高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2307', '伊春市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230717', '伊美区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230718', '乌翠区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230719', '友好区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230722', '嘉荫县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230723', '汤旺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230724', '丰林县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230725', '大箐山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230726', '南岔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230751', '金林区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (763, '230781', '铁力市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2308', '佳木斯市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230803', '向阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230804', '前进区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230805', '东风区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230811', '郊区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230822', '桦南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230826', '桦川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230828', '汤原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230881', '同江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230882', '富锦市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (774, '230883', '抚远市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2309', '七台河市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (785, '230902', '新兴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (785, '230903', '桃山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (785, '230904', '茄子河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (785, '230921', '勃利县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2310', '牡丹江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231002', '东安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231003', '阳明区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231004', '爱民区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231005', '西安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231025', '林口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231081', '绥芬河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231083', '海林市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231084', '宁安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231085', '穆棱市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (790, '231086', '东宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2311', '黑河市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (801, '231102', '爱辉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (801, '231123', '逊克县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (801, '231124', '孙吴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (801, '231181', '北安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (801, '231182', '五大连池市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (801, '231183', '嫩江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2312', '绥化市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231202', '北林区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231221', '望奎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231222', '兰西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231223', '青冈县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231224', '庆安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231225', '明水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231226', '绥棱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231281', '安达市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231282', '肇东市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (808, '231283', '海伦市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (687, '2327', '大兴安岭地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232701', '漠河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232721', '呼玛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232722', '塔河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232761', '加格达奇区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232762', '松岭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232763', '新林区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (819, '232764', '呼中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '31', '上海市', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (827, '3101', '市辖区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310101', '黄浦区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310104', '徐汇区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310105', '长宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310106', '静安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310107', '普陀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310109', '虹口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310110', '杨浦区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310112', '闵行区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310113', '宝山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310114', '嘉定区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310115', '浦东新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310116', '金山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310117', '松江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310118', '青浦区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310120', '奉贤区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (828, '310151', '崇明区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '32', '江苏省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3201', '南京市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320102', '玄武区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320104', '秦淮区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320105', '建邺区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320106', '鼓楼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320111', '浦口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320113', '栖霞区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320114', '雨花台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320115', '江宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320116', '六合区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320117', '溧水区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (846, '320118', '高淳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3202', '无锡市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320205', '锡山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320206', '惠山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320211', '滨湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320213', '梁溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320214', '新吴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320281', '江阴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (858, '320282', '宜兴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3203', '徐州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320302', '鼓楼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320303', '云龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320305', '贾汪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320311', '泉山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320312', '铜山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320321', '丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320322', '沛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320324', '睢宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320371', '徐州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320381', '新沂市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (866, '320382', '邳州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3204', '常州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (878, '320402', '天宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (878, '320404', '钟楼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (878, '320411', '新北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (878, '320412', '武进区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (878, '320413', '金坛区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (878, '320481', '溧阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3205', '苏州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320505', '虎丘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320506', '吴中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320507', '相城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320508', '姑苏区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320509', '吴江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320576', '苏州工业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320581', '常熟市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320582', '张家港市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320583', '昆山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (885, '320585', '太仓市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3206', '南通市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320612', '通州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320613', '崇川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320614', '海门区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320623', '如东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320671', '南通经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320681', '启东市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320682', '如皋市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (896, '320685', '海安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3207', '连云港市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320703', '连云区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320706', '海州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320707', '赣榆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320722', '东海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320723', '灌云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320724', '灌南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (905, '320771', '连云港经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3208', '淮安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320803', '淮安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320804', '淮阴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320812', '清江浦区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320813', '洪泽区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320826', '涟水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320830', '盱眙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320831', '金湖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (913, '320871', '淮安经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3209', '盐城市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320902', '亭湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320903', '盐都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320904', '大丰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320921', '响水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320922', '滨海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320923', '阜宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320924', '射阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320925', '建湖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320971', '盐城经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (922, '320981', '东台市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3210', '扬州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321002', '广陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321003', '邗江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321012', '江都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321023', '宝应县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321071', '扬州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321081', '仪征市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (933, '321084', '高邮市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3211', '镇江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321102', '京口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321111', '润州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321112', '丹徒区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321171', '镇江新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321181', '丹阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321182', '扬中市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (941, '321183', '句容市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3212', '泰州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (949, '321202', '海陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (949, '321203', '高港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (949, '321204', '姜堰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (949, '321281', '兴化市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (949, '321282', '靖江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (949, '321283', '泰兴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (845, '3213', '宿迁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (956, '321302', '宿城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (956, '321311', '宿豫区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (956, '321322', '沭阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (956, '321323', '泗阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (956, '321324', '泗洪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (956, '321371', '宿迁经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '33', '浙江省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3301', '杭州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330102', '上城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330105', '拱墅区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330106', '西湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330108', '滨江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330109', '萧山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330110', '余杭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330111', '富阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330112', '临安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330113', '临平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330114', '钱塘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330122', '桐庐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330127', '淳安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (964, '330182', '建德市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3302', '宁波市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330203', '海曙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330205', '江北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330206', '北仑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330211', '镇海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330212', '鄞州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330213', '奉化区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330225', '象山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330226', '宁海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330281', '余姚市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (978, '330282', '慈溪市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3303', '温州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330302', '鹿城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330303', '龙湾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330304', '瓯海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330305', '洞头区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330324', '永嘉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330326', '平阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330327', '苍南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330328', '文成县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330329', '泰顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330381', '瑞安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330382', '乐清市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (989, '330383', '龙港市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3304', '嘉兴市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330402', '南湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330411', '秀洲区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330421', '嘉善县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330424', '海盐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330481', '海宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330482', '平湖市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1002, '330483', '桐乡市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3305', '湖州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1010, '330502', '吴兴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1010, '330503', '南浔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1010, '330521', '德清县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1010, '330522', '长兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1010, '330523', '安吉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3306', '绍兴市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1016, '330602', '越城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1016, '330603', '柯桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1016, '330604', '上虞区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1016, '330624', '新昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1016, '330681', '诸暨市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1016, '330683', '嵊州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3307', '金华市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330702', '婺城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330703', '金东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330723', '武义县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330726', '浦江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330727', '磐安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330781', '兰溪市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330782', '义乌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330783', '东阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1023, '330784', '永康市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3308', '衢州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1033, '330802', '柯城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1033, '330803', '衢江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1033, '330822', '常山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1033, '330824', '开化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1033, '330825', '龙游县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1033, '330881', '江山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3309', '舟山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1040, '330902', '定海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1040, '330903', '普陀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1040, '330921', '岱山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1040, '330922', '嵊泗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3310', '台州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331002', '椒江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331003', '黄岩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331004', '路桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331022', '三门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331023', '天台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331024', '仙居县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331081', '温岭市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331082', '临海市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1045, '331083', '玉环市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (963, '3311', '丽水市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331102', '莲都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331121', '青田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331122', '缙云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331123', '遂昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331124', '松阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331125', '云和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331126', '庆元县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331127', '景宁畲族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1055, '331181', '龙泉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '34', '安徽省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3401', '合肥市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340102', '瑶海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340103', '庐阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340104', '蜀山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340111', '包河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340121', '长丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340122', '肥东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340123', '肥西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340124', '庐江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340176', '合肥高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340177', '合肥经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340178', '合肥新站高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1066, '340181', '巢湖市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3402', '芜湖市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340202', '镜湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340207', '鸠江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340209', '弋江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340210', '湾沚区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340212', '繁昌区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340223', '南陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340271', '芜湖经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340272', '安徽芜湖三山经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1079, '340281', '无为市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3403', '蚌埠市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340302', '龙子湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340303', '蚌山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340304', '禹会区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340311', '淮上区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340321', '怀远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340322', '五河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340323', '固镇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340371', '蚌埠市高新技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1089, '340372', '蚌埠市经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3404', '淮南市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340402', '大通区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340403', '田家庵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340404', '谢家集区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340405', '八公山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340406', '潘集区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340421', '凤台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1099, '340422', '寿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3405', '马鞍山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1107, '340503', '花山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1107, '340504', '雨山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1107, '340506', '博望区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1107, '340521', '当涂县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1107, '340522', '含山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1107, '340523', '和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3406', '淮北市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1114, '340602', '杜集区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1114, '340603', '相山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1114, '340604', '烈山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1114, '340621', '濉溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3407', '铜陵市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1119, '340705', '铜官区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1119, '340706', '义安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1119, '340711', '郊区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1119, '340722', '枞阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3408', '安庆市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340802', '迎江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340803', '大观区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340811', '宜秀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340822', '怀宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340825', '太湖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340826', '宿松县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340827', '望江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340828', '岳西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340871', '安徽安庆经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340881', '桐城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1124, '340882', '潜山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3410', '黄山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341002', '屯溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341003', '黄山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341004', '徽州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341021', '歙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341022', '休宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341023', '黟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1136, '341024', '祁门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3411', '滁州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341102', '琅琊区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341103', '南谯区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341122', '来安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341124', '全椒县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341125', '定远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341126', '凤阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341171', '中新苏滁高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341172', '滁州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341181', '天长市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1144, '341182', '明光市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3412', '阜阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341202', '颍州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341203', '颍东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341204', '颍泉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341221', '临泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341222', '太和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341225', '阜南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341226', '颍上县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341271', '阜阳合肥现代产业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341272', '阜阳经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1155, '341282', '界首市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3413', '宿州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341302', '埇桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341321', '砀山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341322', '萧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341323', '灵璧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341324', '泗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341371', '宿州马鞍山现代产业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1166, '341372', '宿州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3415', '六安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341502', '金安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341503', '裕安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341504', '叶集区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341522', '霍邱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341523', '舒城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341524', '金寨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1174, '341525', '霍山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3416', '亳州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1182, '341602', '谯城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1182, '341621', '涡阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1182, '341622', '蒙城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1182, '341623', '利辛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3417', '池州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1187, '341702', '贵池区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1187, '341721', '东至县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1187, '341722', '石台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1187, '341723', '青阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1065, '3418', '宣城市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341802', '宣州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341821', '郎溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341823', '泾县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341824', '绩溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341825', '旌德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341871', '宣城市经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341881', '宁国市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1192, '341882', '广德市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '35', '福建省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3501', '福州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350102', '鼓楼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350103', '台江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350104', '仓山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350105', '马尾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350111', '晋安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350112', '长乐区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350121', '闽侯县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350122', '连江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350123', '罗源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350124', '闽清县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350125', '永泰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350128', '平潭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1202, '350181', '福清市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3502', '厦门市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1216, '350203', '思明区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1216, '350205', '海沧区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1216, '350206', '湖里区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1216, '350211', '集美区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1216, '350212', '同安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1216, '350213', '翔安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3503', '莆田市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1223, '350302', '城厢区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1223, '350303', '涵江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1223, '350304', '荔城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1223, '350305', '秀屿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1223, '350322', '仙游县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3504', '三明市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350404', '三元区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350405', '沙县区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350421', '明溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350423', '清流县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350424', '宁化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350425', '大田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350426', '尤溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350428', '将乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350429', '泰宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350430', '建宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1229, '350481', '永安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3505', '泉州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350502', '鲤城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350503', '丰泽区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350504', '洛江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350505', '泉港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350521', '惠安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350524', '安溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350525', '永春县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350526', '德化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350527', '金门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350581', '石狮市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350582', '晋江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1241, '350583', '南安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3506', '漳州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350602', '芗城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350603', '龙文区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350604', '龙海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350605', '长泰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350622', '云霄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350623', '漳浦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350624', '诏安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350626', '东山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350627', '南靖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350628', '平和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1254, '350629', '华安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3507', '南平市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350702', '延平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350703', '建阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350721', '顺昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350722', '浦城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350723', '光泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350724', '松溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350725', '政和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350781', '邵武市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350782', '武夷山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1266, '350783', '建瓯市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3508', '龙岩市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350802', '新罗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350803', '永定区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350821', '长汀县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350823', '上杭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350824', '武平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350825', '连城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1277, '350881', '漳平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1201, '3509', '宁德市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350902', '蕉城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350921', '霞浦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350922', '古田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350923', '屏南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350924', '寿宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350925', '周宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350926', '柘荣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350981', '福安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1285, '350982', '福鼎市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '36', '江西省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3601', '南昌市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360102', '东湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360103', '西湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360104', '青云谱区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360111', '青山湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360112', '新建区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360113', '红谷滩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360121', '南昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360123', '安义县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1296, '360124', '进贤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3602', '景德镇市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1306, '360202', '昌江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1306, '360203', '珠山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1306, '360222', '浮梁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1306, '360281', '乐平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3603', '萍乡市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1311, '360302', '安源区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1311, '360313', '湘东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1311, '360321', '莲花县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1311, '360322', '上栗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1311, '360323', '芦溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3604', '九江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360402', '濂溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360403', '浔阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360404', '柴桑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360423', '武宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360424', '修水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360425', '永修县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360426', '德安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360428', '都昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360429', '湖口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360430', '彭泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360481', '瑞昌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360482', '共青城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1317, '360483', '庐山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3605', '新余市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1331, '360502', '渝水区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1331, '360521', '分宜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3606', '鹰潭市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1334, '360602', '月湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1334, '360603', '余江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1334, '360681', '贵溪市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3607', '赣州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360702', '章贡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360703', '南康区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360704', '赣县区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360722', '信丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360723', '大余县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360724', '上犹县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360725', '崇义县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360726', '安远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360728', '定南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360729', '全南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360730', '宁都县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360731', '于都县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360732', '兴国县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360733', '会昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360734', '寻乌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360735', '石城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360781', '瑞金市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1338, '360783', '龙南市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3608', '吉安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360802', '吉州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360803', '青原区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360821', '吉安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360822', '吉水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360823', '峡江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360824', '新干县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360825', '永丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360826', '泰和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360827', '遂川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360828', '万安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360829', '安福县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360830', '永新县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1357, '360881', '井冈山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3609', '宜春市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360902', '袁州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360921', '奉新县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360922', '万载县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360923', '上高县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360924', '宜丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360925', '靖安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360926', '铜鼓县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360981', '丰城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360982', '樟树市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1371, '360983', '高安市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3610', '抚州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361002', '临川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361003', '东乡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361021', '南城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361022', '黎川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361023', '南丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361024', '崇仁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361025', '乐安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361026', '宜黄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361027', '金溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361028', '资溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1382, '361030', '广昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1295, '3611', '上饶市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361102', '信州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361103', '广丰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361104', '广信区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361123', '玉山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361124', '铅山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361125', '横峰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361126', '弋阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361127', '余干县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361128', '鄱阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361129', '万年县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361130', '婺源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1394, '361181', '德兴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '37', '山东省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3701', '济南市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370102', '历下区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370103', '市中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370104', '槐荫区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370105', '天桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370112', '历城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370113', '长清区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370114', '章丘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370115', '济阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370116', '莱芜区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370117', '钢城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370124', '平阴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370126', '商河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1408, '370176', '济南高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3702', '青岛市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370202', '市南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370203', '市北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370211', '黄岛区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370212', '崂山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370213', '李沧区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370214', '城阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370215', '即墨区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370281', '胶州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370283', '平度市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1422, '370285', '莱西市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3703', '淄博市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370302', '淄川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370303', '张店区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370304', '博山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370305', '临淄区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370306', '周村区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370321', '桓台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370322', '高青县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1433, '370323', '沂源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3704', '枣庄市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1442, '370402', '市中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1442, '370403', '薛城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1442, '370404', '峄城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1442, '370405', '台儿庄区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1442, '370406', '山亭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1442, '370481', '滕州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3705', '东营市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370502', '东营区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370503', '河口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370505', '垦利区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370522', '利津县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370523', '广饶县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370571', '东营经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1449, '370572', '东营港经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3706', '烟台市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370602', '芝罘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370611', '福山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370612', '牟平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370613', '莱山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370614', '蓬莱区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370671', '烟台高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370676', '烟台经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370681', '龙口市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370682', '莱阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370683', '莱州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370685', '招远市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370686', '栖霞市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1457, '370687', '海阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3707', '潍坊市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370702', '潍城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370703', '寒亭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370704', '坊子区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370705', '奎文区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370724', '临朐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370725', '昌乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370772', '潍坊滨海经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370781', '青州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370782', '诸城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370783', '寿光市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370784', '安丘市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370785', '高密市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1471, '370786', '昌邑市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3708', '济宁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370811', '任城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370812', '兖州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370826', '微山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370827', '鱼台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370828', '金乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370829', '嘉祥县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370830', '汶上县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370831', '泗水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370832', '梁山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370871', '济宁高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370881', '曲阜市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1485, '370883', '邹城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3709', '泰安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1498, '370902', '泰山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1498, '370911', '岱岳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1498, '370921', '宁阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1498, '370923', '东平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1498, '370982', '新泰市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1498, '370983', '肥城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3710', '威海市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371002', '环翠区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371003', '文登区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371071', '威海火炬高技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371072', '威海经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371073', '威海临港经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371082', '荣成市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1505, '371083', '乳山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3711', '日照市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1513, '371102', '东港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1513, '371103', '岚山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1513, '371121', '五莲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1513, '371122', '莒县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1513, '371171', '日照经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3713', '临沂市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371302', '兰山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371311', '罗庄区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371312', '河东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371321', '沂南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371322', '郯城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371323', '沂水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371324', '兰陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371325', '费县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371326', '平邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371327', '莒南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371328', '蒙阴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371329', '临沭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1519, '371371', '临沂高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3714', '德州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371402', '德城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371403', '陵城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371422', '宁津县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371423', '庆云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371424', '临邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371425', '齐河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371426', '平原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371427', '夏津县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371428', '武城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371471', '德州天衢新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371481', '乐陵市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1533, '371482', '禹城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3715', '聊城市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371502', '东昌府区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371503', '茌平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371521', '阳谷县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371522', '莘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371524', '东阿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371525', '冠县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371526', '高唐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1546, '371581', '临清市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3716', '滨州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371602', '滨城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371603', '沾化区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371621', '惠民县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371622', '阳信县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371623', '无棣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371625', '博兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1555, '371681', '邹平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1407, '3717', '菏泽市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371702', '牡丹区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371703', '定陶区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371721', '曹县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371722', '单县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371723', '成武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371724', '巨野县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371725', '郓城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371726', '鄄城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371728', '东明县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371771', '菏泽经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1563, '371772', '菏泽高新技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '41', '河南省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4101', '郑州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410102', '中原区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410103', '二七区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410104', '管城回族区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410105', '金水区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410106', '上街区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410108', '惠济区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410122', '中牟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410171', '郑州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410172', '郑州高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410173', '郑州航空港经济综合实验区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410181', '巩义市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410182', '荥阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410183', '新密市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410184', '新郑市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1576, '410185', '登封市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4102', '开封市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410202', '龙亭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410203', '顺河回族区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410204', '鼓楼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410205', '禹王台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410212', '祥符区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410221', '杞县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410222', '通许县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410223', '尉氏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1592, '410225', '兰考县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4103', '洛阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410302', '老城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410303', '西工区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410304', '瀍河回族区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410305', '涧西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410307', '偃师区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410308', '孟津区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410311', '洛龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410323', '新安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410324', '栾川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410325', '嵩县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410326', '汝阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410327', '宜阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410328', '洛宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410329', '伊川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1602, '410371', '洛阳高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4104', '平顶山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410402', '新华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410403', '卫东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410404', '石龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410411', '湛河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410421', '宝丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410422', '叶县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410423', '鲁山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410425', '郏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410471', '平顶山高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410472', '平顶山市城乡一体化示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410481', '舞钢市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1618, '410482', '汝州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4105', '安阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410502', '文峰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410503', '北关区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410505', '殷都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410506', '龙安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410522', '安阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410523', '汤阴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410526', '滑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410527', '内黄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410571', '安阳高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1631, '410581', '林州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4106', '鹤壁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1642, '410602', '鹤山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1642, '410603', '山城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1642, '410611', '淇滨区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1642, '410621', '浚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1642, '410622', '淇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1642, '410671', '鹤壁经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4107', '新乡市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410702', '红旗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410703', '卫滨区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410704', '凤泉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410711', '牧野区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410721', '新乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410724', '获嘉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410725', '原阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410726', '延津县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410727', '封丘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410771', '新乡高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410772', '新乡经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410773', '新乡市平原城乡一体化示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410781', '卫辉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410782', '辉县市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1649, '410783', '长垣市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4108', '焦作市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410802', '解放区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410803', '中站区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410804', '马村区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410811', '山阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410821', '修武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410822', '博爱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410823', '武陟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410825', '温县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410871', '焦作城乡一体化示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410882', '沁阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1665, '410883', '孟州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4109', '濮阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410902', '华龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410922', '清丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410923', '南乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410926', '范县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410927', '台前县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410928', '濮阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410971', '河南濮阳工业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1677, '410972', '濮阳经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4110', '许昌市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411002', '魏都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411003', '建安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411024', '鄢陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411025', '襄城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411071', '许昌经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411081', '禹州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1686, '411082', '长葛市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4111', '漯河市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1694, '411102', '源汇区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1694, '411103', '郾城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1694, '411104', '召陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1694, '411121', '舞阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1694, '411122', '临颍县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1694, '411171', '漯河经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4112', '三门峡市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411202', '湖滨区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411203', '陕州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411221', '渑池县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411224', '卢氏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411271', '河南三门峡经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411281', '义马市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1701, '411282', '灵宝市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4113', '南阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411302', '宛城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411303', '卧龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411321', '南召县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411322', '方城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411323', '西峡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411324', '镇平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411325', '内乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411326', '淅川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411327', '社旗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411328', '唐河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411329', '新野县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411330', '桐柏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411371', '南阳高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411372', '南阳市城乡一体化示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1709, '411381', '邓州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4114', '商丘市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411402', '梁园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411403', '睢阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411421', '民权县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411422', '睢县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411423', '宁陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411424', '柘城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411425', '虞城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411426', '夏邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411471', '豫东综合物流产业聚集区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411472', '河南商丘经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1725, '411481', '永城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4115', '信阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411502', '浉河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411503', '平桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411521', '罗山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411522', '光山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411523', '新县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411524', '商城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411525', '固始县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411526', '潢川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411527', '淮滨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411528', '息县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1737, '411571', '信阳高新技术产业开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4116', '周口市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411602', '川汇区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411603', '淮阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411621', '扶沟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411622', '西华县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411623', '商水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411624', '沈丘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411625', '郸城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411627', '太康县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411628', '鹿邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411671', '周口临港开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1749, '411681', '项城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4117', '驻马店市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411702', '驿城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411721', '西平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411722', '上蔡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411723', '平舆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411724', '正阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411725', '确山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411726', '泌阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411727', '汝南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411728', '遂平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411729', '新蔡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1761, '411771', '河南驻马店经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1575, '4190', '省直辖县级行政区划', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1773, '419001', '济源市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '42', '湖北省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4201', '武汉市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420102', '江岸区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420103', '江汉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420104', '硚口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420105', '汉阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420106', '武昌区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420107', '青山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420111', '洪山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420112', '东西湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420113', '汉南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420114', '蔡甸区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420115', '江夏区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420116', '黄陂区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1776, '420117', '新洲区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4202', '黄石市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1790, '420202', '黄石港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1790, '420203', '西塞山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1790, '420204', '下陆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1790, '420205', '铁山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1790, '420222', '阳新县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1790, '420281', '大冶市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4203', '十堰市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420302', '茅箭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420303', '张湾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420304', '郧阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420322', '郧西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420323', '竹山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420324', '竹溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420325', '房县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1797, '420381', '丹江口市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4205', '宜昌市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420502', '西陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420503', '伍家岗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420504', '点军区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420505', '猇亭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420506', '夷陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420525', '远安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420526', '兴山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420527', '秭归县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420528', '长阳土家族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420529', '五峰土家族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420581', '宜都市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420582', '当阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1806, '420583', '枝江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4206', '襄阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420602', '襄城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420606', '樊城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420607', '襄州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420624', '南漳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420625', '谷城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420626', '保康县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420682', '老河口市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420683', '枣阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1820, '420684', '宜城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4207', '鄂州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1830, '420702', '梁子湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1830, '420703', '华容区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1830, '420704', '鄂城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4208', '荆门市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1834, '420802', '东宝区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1834, '420804', '掇刀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1834, '420822', '沙洋县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1834, '420881', '钟祥市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1834, '420882', '京山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4209', '孝感市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420902', '孝南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420921', '孝昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420922', '大悟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420923', '云梦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420981', '应城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420982', '安陆市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1840, '420984', '汉川市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4210', '荆州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421002', '沙市区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421003', '荆州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421022', '公安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421024', '江陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421071', '荆州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421081', '石首市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421083', '洪湖市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421087', '松滋市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1848, '421088', '监利市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4211', '黄冈市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421102', '黄州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421121', '团风县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421122', '红安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421123', '罗田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421124', '英山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421125', '浠水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421126', '蕲春县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421127', '黄梅县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421171', '龙感湖管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421181', '麻城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1858, '421182', '武穴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4212', '咸宁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1870, '421202', '咸安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1870, '421221', '嘉鱼县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1870, '421222', '通城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1870, '421223', '崇阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1870, '421224', '通山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1870, '421281', '赤壁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4213', '随州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1877, '421303', '曾都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1877, '421321', '随县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1877, '421381', '广水市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4228', '恩施土家族苗族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422801', '恩施市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422802', '利川市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422822', '建始县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422823', '巴东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422825', '宣恩县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422826', '咸丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422827', '来凤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1881, '422828', '鹤峰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1775, '4290', '省直辖县级行政区划', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1890, '429004', '仙桃市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1890, '429005', '潜江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1890, '429006', '天门市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1890, '429021', '神农架林区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '43', '湖南省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4301', '长沙市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430102', '芙蓉区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430103', '天心区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430104', '岳麓区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430105', '开福区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430111', '雨花区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430112', '望城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430121', '长沙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430181', '浏阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1896, '430182', '宁乡市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4302', '株洲市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430202', '荷塘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430203', '芦淞区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430204', '石峰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430211', '天元区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430212', '渌口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430223', '攸县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430224', '茶陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430225', '炎陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1906, '430281', '醴陵市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4303', '湘潭市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430302', '雨湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430304', '岳塘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430321', '湘潭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430371', '湖南湘潭高新技术产业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430372', '湘潭昭山示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430373', '湘潭九华示范区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430381', '湘乡市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1916, '430382', '韶山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4304', '衡阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430405', '珠晖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430406', '雁峰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430407', '石鼓区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430408', '蒸湘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430412', '南岳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430421', '衡阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430422', '衡南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430423', '衡山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430424', '衡东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430426', '祁东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430473', '湖南衡阳松木经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430476', '湖南衡阳高新技术产业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430481', '耒阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1925, '430482', '常宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4305', '邵阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430502', '双清区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430503', '大祥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430511', '北塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430522', '新邵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430523', '邵阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430524', '隆回县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430525', '洞口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430527', '绥宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430528', '新宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430529', '城步苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430581', '武冈市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1940, '430582', '邵东市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4306', '岳阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430602', '岳阳楼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430603', '云溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430611', '君山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430621', '岳阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430623', '华容县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430624', '湘阴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430626', '平江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430671', '岳阳市屈原管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430681', '汨罗市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1953, '430682', '临湘市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4307', '常德市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430702', '武陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430703', '鼎城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430721', '安乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430722', '汉寿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430723', '澧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430724', '临澧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430725', '桃源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430726', '石门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430771', '常德市西洞庭管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1964, '430781', '津市市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4308', '张家界市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1975, '430802', '永定区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1975, '430811', '武陵源区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1975, '430821', '慈利县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1975, '430822', '桑植县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4309', '益阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430902', '资阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430903', '赫山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430921', '南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430922', '桃江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430923', '安化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430971', '益阳市大通湖管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430972', '湖南益阳高新技术产业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1980, '430981', '沅江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4310', '郴州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431002', '北湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431003', '苏仙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431021', '桂阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431022', '宜章县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431023', '永兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431024', '嘉禾县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431025', '临武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431026', '汝城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431027', '桂东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431028', '安仁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1989, '431081', '资兴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4311', '永州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431102', '零陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431103', '冷水滩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431122', '东安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431123', '双牌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431124', '道县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431125', '江永县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431126', '宁远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431127', '蓝山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431128', '新田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431129', '江华瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431171', '永州经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431173', '永州市回龙圩管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2001, '431181', '祁阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4312', '怀化市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431202', '鹤城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431221', '中方县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431222', '沅陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431223', '辰溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431224', '溆浦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431225', '会同县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431226', '麻阳苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431227', '新晃侗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431228', '芷江侗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431229', '靖州苗族侗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431230', '通道侗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431271', '怀化市洪江管理区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2015, '431281', '洪江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4313', '娄底市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2029, '431302', '娄星区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2029, '431321', '双峰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2029, '431322', '新化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2029, '431381', '冷水江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2029, '431382', '涟源市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (1895, '4331', '湘西土家族苗族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433101', '吉首市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433122', '泸溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433123', '凤凰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433124', '花垣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433125', '保靖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433126', '古丈县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433127', '永顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2035, '433130', '龙山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '44', '广东省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4401', '广州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440103', '荔湾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440104', '越秀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440105', '海珠区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440106', '天河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440111', '白云区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440112', '黄埔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440113', '番禺区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440114', '花都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440115', '南沙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440117', '从化区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2045, '440118', '增城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4402', '韶关市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440203', '武江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440204', '浈江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440205', '曲江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440222', '始兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440224', '仁化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440229', '翁源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440232', '乳源瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440233', '新丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440281', '乐昌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2057, '440282', '南雄市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4403', '深圳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440303', '罗湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440304', '福田区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440305', '南山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440306', '宝安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440307', '龙岗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440308', '盐田区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440309', '龙华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440310', '坪山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2068, '440311', '光明区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4404', '珠海市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2078, '440402', '香洲区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2078, '440403', '斗门区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2078, '440404', '金湾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4405', '汕头市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440507', '龙湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440511', '金平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440512', '濠江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440513', '潮阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440514', '潮南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440515', '澄海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2082, '440523', '南澳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4406', '佛山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2090, '440604', '禅城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2090, '440605', '南海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2090, '440606', '顺德区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2090, '440607', '三水区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2090, '440608', '高明区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4407', '江门市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440703', '蓬江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440704', '江海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440705', '新会区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440781', '台山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440783', '开平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440784', '鹤山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2096, '440785', '恩平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4408', '湛江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440802', '赤坎区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440803', '霞山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440804', '坡头区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440811', '麻章区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440823', '遂溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440825', '徐闻县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440881', '廉江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440882', '雷州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2104, '440883', '吴川市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4409', '茂名市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2114, '440902', '茂南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2114, '440904', '电白区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2114, '440981', '高州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2114, '440982', '化州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2114, '440983', '信宜市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4412', '肇庆市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441202', '端州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441203', '鼎湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441204', '高要区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441223', '广宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441224', '怀集县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441225', '封开县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441226', '德庆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2120, '441284', '四会市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4413', '惠州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2129, '441302', '惠城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2129, '441303', '惠阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2129, '441322', '博罗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2129, '441323', '惠东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2129, '441324', '龙门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4414', '梅州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441402', '梅江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441403', '梅县区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441422', '大埔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441423', '丰顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441424', '五华县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441426', '平远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441427', '蕉岭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2135, '441481', '兴宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4415', '汕尾市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2144, '441502', '城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2144, '441521', '海丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2144, '441523', '陆河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2144, '441581', '陆丰市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4416', '河源市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2149, '441602', '源城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2149, '441621', '紫金县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2149, '441622', '龙川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2149, '441623', '连平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2149, '441624', '和平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2149, '441625', '东源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4417', '阳江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2156, '441702', '江城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2156, '441704', '阳东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2156, '441721', '阳西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2156, '441781', '阳春市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4418', '清远市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441802', '清城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441803', '清新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441821', '佛冈县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441823', '阳山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441825', '连山壮族瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441826', '连南瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441881', '英德市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2161, '441882', '连州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4419', '东莞市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900003', '东城街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900004', '南城街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900005', '万江街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900006', '莞城街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900101', '石碣镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900102', '石龙镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900103', '茶山镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900104', '石排镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900105', '企石镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900106', '横沥镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900107', '桥头镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900108', '谢岗镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900109', '东坑镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900110', '常平镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900111', '寮步镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900112', '樟木头镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900113', '大朗镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900114', '黄江镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900115', '清溪镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900116', '塘厦镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900117', '凤岗镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900118', '大岭山镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900119', '长安镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900121', '虎门镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900122', '厚街镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900123', '沙田镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900124', '道滘镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900125', '洪梅镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900126', '麻涌镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900127', '望牛墩镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900128', '中堂镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900129', '高埗镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900401', '松山湖', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900402', '东莞港', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900403', '东莞生态园', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2170, '441900404', '东莞滨海湾新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4420', '中山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000001', '石岐街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000002', '东区街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000003', '中山港街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000004', '西区街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000005', '南区街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000006', '五桂山街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000007', '民众街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000008', '南朗街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000101', '黄圃镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000103', '东凤镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000105', '古镇镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000106', '沙溪镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000107', '坦洲镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000108', '港口镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000109', '三角镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000110', '横栏镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000111', '南头镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000112', '阜沙镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000114', '三乡镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000115', '板芙镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000116', '大涌镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000117', '神湾镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2207, '442000118', '小榄镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4451', '潮州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2231, '445102', '湘桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2231, '445103', '潮安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2231, '445122', '饶平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4452', '揭阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2235, '445202', '榕城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2235, '445203', '揭东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2235, '445222', '揭西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2235, '445224', '惠来县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2235, '445281', '普宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2044, '4453', '云浮市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2241, '445302', '云城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2241, '445303', '云安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2241, '445321', '新兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2241, '445322', '郁南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2241, '445381', '罗定市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '45', '广西壮族自治区', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4501', '南宁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450102', '兴宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450103', '青秀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450105', '江南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450107', '西乡塘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450108', '良庆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450109', '邕宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450110', '武鸣区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450123', '隆安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450124', '马山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450125', '上林县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450126', '宾阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2248, '450181', '横州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4502', '柳州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450202', '城中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450203', '鱼峰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450204', '柳南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450205', '柳北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450206', '柳江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450222', '柳城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450223', '鹿寨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450224', '融安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450225', '融水苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2261, '450226', '三江侗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4503', '桂林市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450302', '秀峰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450303', '叠彩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450304', '象山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450305', '七星区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450311', '雁山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450312', '临桂区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450321', '阳朔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450323', '灵川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450324', '全州县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450325', '兴安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450326', '永福县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450327', '灌阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450328', '龙胜各族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450329', '资源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450330', '平乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450332', '恭城瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2272, '450381', '荔浦市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4504', '梧州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450403', '万秀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450405', '长洲区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450406', '龙圩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450421', '苍梧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450422', '藤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450423', '蒙山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2290, '450481', '岑溪市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4505', '北海市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2298, '450502', '海城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2298, '450503', '银海区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2298, '450512', '铁山港区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2298, '450521', '合浦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4506', '防城港市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2303, '450602', '港口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2303, '450603', '防城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2303, '450621', '上思县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2303, '450681', '东兴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4507', '钦州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2308, '450702', '钦南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2308, '450703', '钦北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2308, '450721', '灵山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2308, '450722', '浦北县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4508', '贵港市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2313, '450802', '港北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2313, '450803', '港南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2313, '450804', '覃塘区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2313, '450821', '平南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2313, '450881', '桂平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4509', '玉林市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450902', '玉州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450903', '福绵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450921', '容县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450922', '陆川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450923', '博白县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450924', '兴业县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2319, '450981', '北流市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4510', '百色市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451002', '右江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451003', '田阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451022', '田东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451024', '德保县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451026', '那坡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451027', '凌云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451028', '乐业县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451029', '田林县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451030', '西林县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451031', '隆林各族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451081', '靖西市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2327, '451082', '平果市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4511', '贺州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2340, '451102', '八步区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2340, '451103', '平桂区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2340, '451121', '昭平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2340, '451122', '钟山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2340, '451123', '富川瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4512', '河池市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451202', '金城江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451203', '宜州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451221', '南丹县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451222', '天峨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451223', '凤山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451224', '东兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451225', '罗城仫佬族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451226', '环江毛南族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451227', '巴马瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451228', '都安瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2346, '451229', '大化瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4513', '来宾市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2358, '451302', '兴宾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2358, '451321', '忻城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2358, '451322', '象州县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2358, '451323', '武宣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2358, '451324', '金秀瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2358, '451381', '合山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2247, '4514', '崇左市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451402', '江州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451421', '扶绥县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451422', '宁明县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451423', '龙州县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451424', '大新县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451425', '天等县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2365, '451481', '凭祥市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '46', '海南省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2373, '4601', '海口市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2374, '460105', '秀英区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2374, '460106', '龙华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2374, '460107', '琼山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2374, '460108', '美兰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2373, '4602', '三亚市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2379, '460202', '海棠区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2379, '460203', '吉阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2379, '460204', '天涯区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2379, '460205', '崖州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2373, '4603', '三沙市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2384, '460321', '西沙群岛', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2384, '460322', '南沙群岛', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2384, '460323', '中沙群岛的岛礁及其海域', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2373, '4604', '儋州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400100', '那大镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400101', '和庆镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400102', '南丰镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400103', '大成镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400104', '雅星镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400105', '兰洋镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400106', '光村镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400107', '木棠镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400108', '海头镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400109', '峨蔓镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400111', '王五镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400112', '白马井镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400113', '中和镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400114', '排浦镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400115', '东成镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400116', '新州镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400499', '洋浦经济开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2388, '460400500', '华南热作学院', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2373, '4690', '省直辖县级行政区划', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469001', '五指山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469002', '琼海市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469005', '文昌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469006', '万宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469007', '东方市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469021', '定安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469022', '屯昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469023', '澄迈县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469024', '临高县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469025', '白沙黎族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469026', '昌江黎族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469027', '乐东黎族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469028', '陵水黎族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469029', '保亭黎族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2407, '469030', '琼中黎族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '50', '重庆市', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2423, '5001', '市辖区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500101', '万州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500102', '涪陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500103', '渝中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500104', '大渡口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500105', '江北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500106', '沙坪坝区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500107', '九龙坡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500108', '南岸区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500109', '北碚区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500110', '綦江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500111', '大足区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500112', '渝北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500113', '巴南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500114', '黔江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500115', '长寿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500116', '江津区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500117', '合川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500118', '永川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500119', '南川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500120', '璧山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500151', '铜梁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500152', '潼南区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500153', '荣昌区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500154', '开州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500155', '梁平区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2424, '500156', '武隆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2423, '5002', '县', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500229', '城口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500230', '丰都县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500231', '垫江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500233', '忠县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500235', '云阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500236', '奉节县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500237', '巫山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500238', '巫溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500240', '石柱土家族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500241', '秀山土家族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500242', '酉阳土家族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2451, '500243', '彭水苗族土家族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '51', '四川省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5101', '成都市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510104', '锦江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510105', '青羊区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510106', '金牛区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510107', '武侯区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510108', '成华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510112', '龙泉驿区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510113', '青白江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510114', '新都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510115', '温江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510116', '双流区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510117', '郫都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510118', '新津区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510121', '金堂县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510129', '大邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510131', '蒲江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510181', '都江堰市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510182', '彭州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510183', '邛崃市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510184', '崇州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2465, '510185', '简阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5103', '自贡市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2486, '510302', '自流井区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2486, '510303', '贡井区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2486, '510304', '大安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2486, '510311', '沿滩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2486, '510321', '荣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2486, '510322', '富顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5104', '攀枝花市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2493, '510402', '东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2493, '510403', '西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2493, '510411', '仁和区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2493, '510421', '米易县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2493, '510422', '盐边县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5105', '泸州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510502', '江阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510503', '纳溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510504', '龙马潭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510521', '泸县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510522', '合江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510524', '叙永县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2499, '510525', '古蔺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5106', '德阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2507, '510603', '旌阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2507, '510604', '罗江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2507, '510623', '中江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2507, '510681', '广汉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2507, '510682', '什邡市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2507, '510683', '绵竹市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5107', '绵阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510703', '涪城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510704', '游仙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510705', '安州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510722', '三台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510723', '盐亭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510725', '梓潼县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510726', '北川羌族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510727', '平武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2514, '510781', '江油市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5108', '广元市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510802', '利州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510811', '昭化区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510812', '朝天区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510821', '旺苍县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510822', '青川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510823', '剑阁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2524, '510824', '苍溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5109', '遂宁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2532, '510903', '船山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2532, '510904', '安居区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2532, '510921', '蓬溪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2532, '510923', '大英县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2532, '510981', '射洪市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5110', '内江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2538, '511002', '市中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2538, '511011', '东兴区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2538, '511024', '威远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2538, '511025', '资中县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2538, '511083', '隆昌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5111', '乐山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511102', '市中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511111', '沙湾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511112', '五通桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511113', '金口河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511123', '犍为县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511124', '井研县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511126', '夹江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511129', '沐川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511132', '峨边彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511133', '马边彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2544, '511181', '峨眉山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5113', '南充市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511302', '顺庆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511303', '高坪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511304', '嘉陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511321', '南部县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511322', '营山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511323', '蓬安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511324', '仪陇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511325', '西充县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2556, '511381', '阆中市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5114', '眉山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2566, '511402', '东坡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2566, '511403', '彭山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2566, '511421', '仁寿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2566, '511423', '洪雅县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2566, '511424', '丹棱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2566, '511425', '青神县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5115', '宜宾市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511502', '翠屏区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511503', '南溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511504', '叙州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511523', '江安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511524', '长宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511525', '高县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511526', '珙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511527', '筠连县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511528', '兴文县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2573, '511529', '屏山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5116', '广安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2584, '511602', '广安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2584, '511603', '前锋区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2584, '511621', '岳池县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2584, '511622', '武胜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2584, '511623', '邻水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2584, '511681', '华蓥市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5117', '达州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511702', '通川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511703', '达川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511722', '宣汉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511723', '开江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511724', '大竹县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511725', '渠县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2591, '511781', '万源市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5118', '雅安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511802', '雨城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511803', '名山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511822', '荥经县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511823', '汉源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511824', '石棉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511825', '天全县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511826', '芦山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2599, '511827', '宝兴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5119', '巴中市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2608, '511902', '巴州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2608, '511903', '恩阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2608, '511921', '通江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2608, '511922', '南江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2608, '511923', '平昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5120', '资阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2614, '512002', '雁江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2614, '512021', '安岳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2614, '512022', '乐至县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5132', '阿坝藏族羌族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513201', '马尔康市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513221', '汶川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513222', '理县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513223', '茂县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513224', '松潘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513225', '九寨沟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513226', '金川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513227', '小金县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513228', '黑水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513230', '壤塘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513231', '阿坝县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513232', '若尔盖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2618, '513233', '红原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5133', '甘孜藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513301', '康定市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513322', '泸定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513323', '丹巴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513324', '九龙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513325', '雅江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513326', '道孚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513327', '炉霍县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513328', '甘孜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513329', '新龙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513330', '德格县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513331', '白玉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513332', '石渠县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513333', '色达县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513334', '理塘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513335', '巴塘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513336', '乡城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513337', '稻城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2632, '513338', '得荣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2464, '5134', '凉山彝族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513401', '西昌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513402', '会理市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513422', '木里藏族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513423', '盐源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513424', '德昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513426', '会东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513427', '宁南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513428', '普格县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513429', '布拖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513430', '金阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513431', '昭觉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513432', '喜德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513433', '冕宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513434', '越西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513435', '甘洛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513436', '美姑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2651, '513437', '雷波县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '52', '贵州省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5201', '贵阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520102', '南明区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520103', '云岩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520111', '花溪区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520112', '乌当区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520113', '白云区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520115', '观山湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520121', '开阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520122', '息烽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520123', '修文县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2670, '520181', '清镇市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5202', '六盘水市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2681, '520201', '钟山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2681, '520203', '六枝特区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2681, '520204', '水城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2681, '520281', '盘州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5203', '遵义市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520302', '红花岗区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520303', '汇川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520304', '播州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520322', '桐梓县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520323', '绥阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520324', '正安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520325', '道真仡佬族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520326', '务川仡佬族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520327', '凤冈县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520328', '湄潭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520329', '余庆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520330', '习水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520381', '赤水市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2686, '520382', '仁怀市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5204', '安顺市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2701, '520402', '西秀区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2701, '520403', '平坝区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2701, '520422', '普定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2701, '520423', '镇宁布依族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2701, '520424', '关岭布依族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2701, '520425', '紫云苗族布依族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5205', '毕节市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520502', '七星关区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520521', '大方县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520523', '金沙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520524', '织金县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520525', '纳雍县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520526', '威宁彝族回族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520527', '赫章县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2708, '520581', '黔西市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5206', '铜仁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520602', '碧江区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520603', '万山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520621', '江口县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520622', '玉屏侗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520623', '石阡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520624', '思南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520625', '印江土家族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520626', '德江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520627', '沿河土家族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2717, '520628', '松桃苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5223', '黔西南布依族苗族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522301', '兴义市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522302', '兴仁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522323', '普安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522324', '晴隆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522325', '贞丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522326', '望谟县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522327', '册亨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2728, '522328', '安龙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5226', '黔东南苗族侗族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522601', '凯里市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522622', '黄平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522623', '施秉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522624', '三穗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522625', '镇远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522626', '岑巩县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522627', '天柱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522628', '锦屏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522629', '剑河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522630', '台江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522631', '黎平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522632', '榕江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522633', '从江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522634', '雷山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522635', '麻江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2737, '522636', '丹寨县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2669, '5227', '黔南布依族苗族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522701', '都匀市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522702', '福泉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522722', '荔波县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522723', '贵定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522725', '瓮安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522726', '独山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522727', '平塘县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522728', '罗甸县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522729', '长顺县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522730', '龙里县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522731', '惠水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2754, '522732', '三都水族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '53', '云南省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5301', '昆明市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530102', '五华区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530103', '盘龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530111', '官渡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530112', '西山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530113', '东川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530114', '呈贡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530115', '晋宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530124', '富民县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530125', '宜良县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530126', '石林彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530127', '嵩明县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530128', '禄劝彝族苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530129', '寻甸回族彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2768, '530181', '安宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5303', '曲靖市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530302', '麒麟区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530303', '沾益区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530304', '马龙区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530322', '陆良县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530323', '师宗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530324', '罗平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530325', '富源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530326', '会泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2783, '530381', '宣威市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5304', '玉溪市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530402', '红塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530403', '江川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530423', '通海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530424', '华宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530425', '易门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530426', '峨山彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530427', '新平彝族傣族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530428', '元江哈尼族彝族傣族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2793, '530481', '澄江市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5305', '保山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2803, '530502', '隆阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2803, '530521', '施甸县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2803, '530523', '龙陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2803, '530524', '昌宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2803, '530581', '腾冲市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5306', '昭通市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530602', '昭阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530621', '鲁甸县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530622', '巧家县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530623', '盐津县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530624', '大关县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530625', '永善县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530626', '绥江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530627', '镇雄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530628', '彝良县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530629', '威信县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2809, '530681', '水富市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5307', '丽江市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2821, '530702', '古城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2821, '530721', '玉龙纳西族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2821, '530722', '永胜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2821, '530723', '华坪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2821, '530724', '宁蒗彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5308', '普洱市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530802', '思茅区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530821', '宁洱哈尼族彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530822', '墨江哈尼族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530823', '景东彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530824', '景谷傣族彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530825', '镇沅彝族哈尼族拉祜族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530826', '江城哈尼族彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530827', '孟连傣族拉祜族佤族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530828', '澜沧拉祜族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2827, '530829', '西盟佤族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5309', '临沧市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530902', '临翔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530921', '凤庆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530922', '云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530923', '永德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530924', '镇康县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530925', '双江拉祜族佤族布朗族傣族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530926', '耿马傣族佤族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2838, '530927', '沧源佤族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5323', '楚雄彝族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532301', '楚雄市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532302', '禄丰市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532322', '双柏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532323', '牟定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532324', '南华县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532325', '姚安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532326', '大姚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532327', '永仁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532328', '元谋县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2847, '532329', '武定县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5325', '红河哈尼族彝族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532501', '个旧市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532502', '开远市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532503', '蒙自市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532504', '弥勒市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532523', '屏边苗族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532524', '建水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532525', '石屏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532527', '泸西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532528', '元阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532529', '红河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532530', '金平苗族瑶族傣族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532531', '绿春县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2858, '532532', '河口瑶族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5326', '文山壮族苗族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532601', '文山市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532622', '砚山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532623', '西畴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532624', '麻栗坡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532625', '马关县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532626', '丘北县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532627', '广南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2872, '532628', '富宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5328', '西双版纳傣族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2881, '532801', '景洪市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2881, '532822', '勐海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2881, '532823', '勐腊县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5329', '大理白族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532901', '大理市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532922', '漾濞彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532923', '祥云县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532924', '宾川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532925', '弥渡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532926', '南涧彝族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532927', '巍山彝族回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532928', '永平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532929', '云龙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532930', '洱源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532931', '剑川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2885, '532932', '鹤庆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5331', '德宏傣族景颇族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2898, '533102', '瑞丽市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2898, '533103', '芒市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2898, '533122', '梁河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2898, '533123', '盈江县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2898, '533124', '陇川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5333', '怒江傈僳族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2904, '533301', '泸水市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2904, '533323', '福贡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2904, '533324', '贡山独龙族怒族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2904, '533325', '兰坪白族普米族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2767, '5334', '迪庆藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2909, '533401', '香格里拉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2909, '533422', '德钦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2909, '533423', '维西傈僳族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '54', '西藏自治区', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5401', '拉萨市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540102', '城关区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540103', '堆龙德庆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540104', '达孜区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540121', '林周县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540122', '当雄县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540123', '尼木县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540124', '曲水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540127', '墨竹工卡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540171', '格尔木藏青工业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540172', '拉萨经济技术开发区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540173', '西藏文化旅游创意园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2914, '540174', '达孜工业园区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5402', '日喀则市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540202', '桑珠孜区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540221', '南木林县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540222', '江孜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540223', '定日县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540224', '萨迦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540225', '拉孜县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540226', '昂仁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540227', '谢通门县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540228', '白朗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540229', '仁布县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540230', '康马县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540231', '定结县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540232', '仲巴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540233', '亚东县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540234', '吉隆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540235', '聂拉木县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540236', '萨嘎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2927, '540237', '岗巴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5403', '昌都市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540302', '卡若区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540321', '江达县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540322', '贡觉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540323', '类乌齐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540324', '丁青县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540325', '察雅县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540326', '八宿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540327', '左贡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540328', '芒康县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540329', '洛隆县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2946, '540330', '边坝县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5404', '林芝市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540402', '巴宜区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540421', '工布江达县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540423', '墨脱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540424', '波密县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540425', '察隅县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540426', '朗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2958, '540481', '米林市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5405', '山南市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540502', '乃东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540521', '扎囊县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540522', '贡嘎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540523', '桑日县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540524', '琼结县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540525', '曲松县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540526', '措美县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540527', '洛扎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540528', '加查县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540529', '隆子县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540531', '浪卡子县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2966, '540581', '错那市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5406', '那曲市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540602', '色尼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540621', '嘉黎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540622', '比如县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540623', '聂荣县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540624', '安多县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540625', '申扎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540626', '索县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540627', '班戈县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540628', '巴青县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540629', '尼玛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2979, '540630', '双湖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2913, '5425', '阿里地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542521', '普兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542522', '札达县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542523', '噶尔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542524', '日土县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542525', '革吉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542526', '改则县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2991, '542527', '措勤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '61', '陕西省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6101', '西安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610102', '新城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610103', '碑林区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610104', '莲湖区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610111', '灞桥区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610112', '未央区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610113', '雁塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610114', '阎良区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610115', '临潼区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610116', '长安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610117', '高陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610118', '鄠邑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610122', '蓝田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3000, '610124', '周至县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6102', '铜川市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3014, '610202', '王益区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3014, '610203', '印台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3014, '610204', '耀州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3014, '610222', '宜君县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6103', '宝鸡市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610302', '渭滨区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610303', '金台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610304', '陈仓区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610305', '凤翔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610323', '岐山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610324', '扶风县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610326', '眉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610327', '陇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610328', '千阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610329', '麟游县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610330', '凤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3019, '610331', '太白县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6104', '咸阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610402', '秦都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610403', '杨陵区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610404', '渭城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610422', '三原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610423', '泾阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610424', '乾县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610425', '礼泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610426', '永寿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610428', '长武县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610429', '旬邑县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610430', '淳化县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610431', '武功县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610481', '兴平市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3032, '610482', '彬州市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6105', '渭南市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610502', '临渭区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610503', '华州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610522', '潼关县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610523', '大荔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610524', '合阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610525', '澄城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610526', '蒲城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610527', '白水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610528', '富平县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610581', '韩城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3047, '610582', '华阴市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6106', '延安市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610602', '宝塔区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610603', '安塞区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610621', '延长县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610622', '延川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610625', '志丹县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610626', '吴起县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610627', '甘泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610628', '富县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610629', '洛川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610630', '宜川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610631', '黄龙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610632', '黄陵县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3059, '610681', '子长市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6107', '汉中市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610702', '汉台区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610703', '南郑区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610722', '城固县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610723', '洋县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610724', '西乡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610725', '勉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610726', '宁强县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610727', '略阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610728', '镇巴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610729', '留坝县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3073, '610730', '佛坪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6108', '榆林市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610802', '榆阳区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610803', '横山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610822', '府谷县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610824', '靖边县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610825', '定边县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610826', '绥德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610827', '米脂县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610828', '佳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610829', '吴堡县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610830', '清涧县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610831', '子洲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3085, '610881', '神木市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6109', '安康市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610902', '汉滨区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610921', '汉阴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610922', '石泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610923', '宁陕县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610924', '紫阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610925', '岚皋县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610926', '平利县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610927', '镇坪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610929', '白河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3098, '610981', '旬阳市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (2999, '6110', '商洛市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611002', '商州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611021', '洛南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611022', '丹凤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611023', '商南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611024', '山阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611025', '镇安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3109, '611026', '柞水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '62', '甘肃省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6201', '兰州市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620102', '城关区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620103', '七里河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620104', '西固区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620105', '安宁区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620111', '红古区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620121', '永登县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620122', '皋兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620123', '榆中县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3118, '620171', '兰州新区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6202', '嘉峪关市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3128, '620201001', '雄关街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3128, '620201002', '钢城街道', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3128, '620201100', '新城镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3128, '620201101', '峪泉镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3128, '620201102', '文殊镇', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6203', '金昌市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3134, '620302', '金川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3134, '620321', '永昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6204', '白银市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3137, '620402', '白银区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3137, '620403', '平川区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3137, '620421', '靖远县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3137, '620422', '会宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3137, '620423', '景泰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6205', '天水市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620502', '秦州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620503', '麦积区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620521', '清水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620522', '秦安县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620523', '甘谷县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620524', '武山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3143, '620525', '张家川回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6206', '武威市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3151, '620602', '凉州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3151, '620621', '民勤县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3151, '620622', '古浪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3151, '620623', '天祝藏族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6207', '张掖市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3156, '620702', '甘州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3156, '620721', '肃南裕固族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3156, '620722', '民乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3156, '620723', '临泽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3156, '620724', '高台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3156, '620725', '山丹县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6208', '平凉市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620802', '崆峒区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620821', '泾川县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620822', '灵台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620823', '崇信县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620825', '庄浪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620826', '静宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3163, '620881', '华亭市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6209', '酒泉市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620902', '肃州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620921', '金塔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620922', '瓜州县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620923', '肃北蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620924', '阿克塞哈萨克族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620981', '玉门市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3171, '620982', '敦煌市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6210', '庆阳市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621002', '西峰区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621021', '庆城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621022', '环县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621023', '华池县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621024', '合水县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621025', '正宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621026', '宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3179, '621027', '镇原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6211', '定西市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621102', '安定区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621121', '通渭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621122', '陇西县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621123', '渭源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621124', '临洮县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621125', '漳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3188, '621126', '岷县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6212', '陇南市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621202', '武都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621221', '成县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621222', '文县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621223', '宕昌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621224', '康县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621225', '西和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621226', '礼县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621227', '徽县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3196, '621228', '两当县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6229', '临夏回族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622901', '临夏市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622921', '临夏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622922', '康乐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622923', '永靖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622924', '广河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622925', '和政县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622926', '东乡族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3206, '622927', '积石山保安族东乡族撒拉族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3117, '6230', '甘南藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623001', '合作市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623021', '临潭县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623022', '卓尼县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623023', '舟曲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623024', '迭部县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623025', '玛曲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623026', '碌曲县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3215, '623027', '夏河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '63', '青海省', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6301', '西宁市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630102', '城东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630103', '城中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630104', '城西区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630105', '城北区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630106', '湟中区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630121', '大通回族土族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3225, '630123', '湟源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6302', '海东市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3233, '630202', '乐都区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3233, '630203', '平安区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3233, '630222', '民和回族土族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3233, '630223', '互助土族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3233, '630224', '化隆回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3233, '630225', '循化撒拉族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6322', '海北藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3240, '632221', '门源回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3240, '632222', '祁连县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3240, '632223', '海晏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3240, '632224', '刚察县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6323', '黄南藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3245, '632301', '同仁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3245, '632322', '尖扎县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3245, '632323', '泽库县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3245, '632324', '河南蒙古族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6325', '海南藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3250, '632521', '共和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3250, '632522', '同德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3250, '632523', '贵德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3250, '632524', '兴海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3250, '632525', '贵南县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6326', '果洛藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3256, '632621', '玛沁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3256, '632622', '班玛县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3256, '632623', '甘德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3256, '632624', '达日县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3256, '632625', '久治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3256, '632626', '玛多县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6327', '玉树藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3263, '632701', '玉树市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3263, '632722', '杂多县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3263, '632723', '称多县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3263, '632724', '治多县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3263, '632725', '囊谦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3263, '632726', '曲麻莱县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3224, '6328', '海西蒙古族藏族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632801', '格尔木市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632802', '德令哈市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632803', '茫崖市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632821', '乌兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632822', '都兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632823', '天峻县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3270, '632857', '大柴旦行政委员会', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '64', '宁夏回族自治区', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3278, '6401', '银川市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3279, '640104', '兴庆区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3279, '640105', '西夏区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3279, '640106', '金凤区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3279, '640121', '永宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3279, '640122', '贺兰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3279, '640181', '灵武市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3278, '6402', '石嘴山市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3286, '640202', '大武口区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3286, '640205', '惠农区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3286, '640221', '平罗县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3278, '6403', '吴忠市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3290, '640302', '利通区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3290, '640303', '红寺堡区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3290, '640323', '盐池县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3290, '640324', '同心县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3290, '640381', '青铜峡市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3278, '6404', '固原市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3296, '640402', '原州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3296, '640422', '西吉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3296, '640423', '隆德县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3296, '640424', '泾源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3296, '640425', '彭阳县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3278, '6405', '中卫市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3302, '640502', '沙坡头区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3302, '640521', '中宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3302, '640522', '海原县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (0, '65', '新疆维吾尔自治区', 1, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6501', '乌鲁木齐市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650102', '天山区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650103', '沙依巴克区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650104', '新市区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650105', '水磨沟区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650106', '头屯河区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650107', '达坂城区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650109', '米东区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3307, '650121', '乌鲁木齐县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6502', '克拉玛依市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3316, '650202', '独山子区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3316, '650203', '克拉玛依区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3316, '650204', '白碱滩区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3316, '650205', '乌尔禾区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6504', '吐鲁番市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3321, '650402', '高昌区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3321, '650421', '鄯善县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3321, '650422', '托克逊县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6505', '哈密市', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3325, '650502', '伊州区', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3325, '650521', '巴里坤哈萨克自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3325, '650522', '伊吾县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6523', '昌吉回族自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652301', '昌吉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652302', '阜康市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652323', '呼图壁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652324', '玛纳斯县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652325', '奇台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652327', '吉木萨尔县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3329, '652328', '木垒哈萨克自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6527', '博尔塔拉蒙古自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3337, '652701', '博乐市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3337, '652702', '阿拉山口市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3337, '652722', '精河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3337, '652723', '温泉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6528', '巴音郭楞蒙古自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652801', '库尔勒市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652822', '轮台县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652823', '尉犁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652824', '若羌县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652825', '且末县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652826', '焉耆回族自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652827', '和静县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652828', '和硕县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3342, '652829', '博湖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6529', '阿克苏地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652901', '阿克苏市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652902', '库车市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652922', '温宿县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652924', '沙雅县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652925', '新和县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652926', '拜城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652927', '乌什县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652928', '阿瓦提县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3352, '652929', '柯坪县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6530', '克孜勒苏柯尔克孜自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3362, '653001', '阿图什市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3362, '653022', '阿克陶县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3362, '653023', '阿合奇县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3362, '653024', '乌恰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6531', '喀什地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653101', '喀什市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653121', '疏附县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653122', '疏勒县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653123', '英吉沙县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653124', '泽普县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653125', '莎车县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653126', '叶城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653127', '麦盖提县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653128', '岳普湖县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653129', '伽师县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653130', '巴楚县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3367, '653131', '塔什库尔干塔吉克自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6532', '和田地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653201', '和田市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653221', '和田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653222', '墨玉县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653223', '皮山县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653224', '洛浦县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653225', '策勒县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653226', '于田县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3380, '653227', '民丰县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6540', '伊犁哈萨克自治州', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654002', '伊宁市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654003', '奎屯市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654004', '霍尔果斯市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654021', '伊宁县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654022', '察布查尔锡伯自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654023', '霍城县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654024', '巩留县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654025', '新源县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654026', '昭苏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654027', '特克斯县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3389, '654028', '尼勒克县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6542', '塔城地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654201', '塔城市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654202', '乌苏市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654203', '沙湾市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654221', '额敏县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654224', '托里县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654225', '裕民县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3401, '654226', '和布克赛尔蒙古自治县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6543', '阿勒泰地区', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654301', '阿勒泰市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654321', '布尔津县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654322', '富蕴县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654323', '福海县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654324', '哈巴河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654325', '青河县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3409, '654326', '吉木乃县', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3306, '6590', '自治区直辖县级行政区划', 2, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659001', '石河子市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659002', '阿拉尔市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659003', '图木舒克市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659004', '五家渠市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659005', '北屯市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659006', '铁门关市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659007', '双河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659008', '可克达拉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659009', '昆玉市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659010', '胡杨河市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659011', '新星市', 3, 0);
INSERT INTO t_region (parent_id, code, name, level, sort)
VALUES (3417, '659012', '白杨市', 3, 0);

-- ===============================================================
-- PART 3  用户收货地址表（t_shipping_address）
-- ===============================================================

DROP TABLE IF EXISTS t_shipping_address CASCADE;
-- 用户收货地址表：支持多地址 + 默认地址 + 省市区三级
CREATE TABLE t_shipping_address
(
    id             BIGINT IDENTITY(1, 1) NOT NULL,
    user_id        BIGINT       NOT NULL,
    receiver_name  VARCHAR(64)  NOT NULL,
    receiver_phone VARCHAR(32)  NOT NULL,
    province       VARCHAR(32)  NOT NULL,
    city           VARCHAR(32)  NOT NULL,
    district       VARCHAR(32)  NOT NULL,
    detail         VARCHAR(255) NOT NULL,
    is_default     CHAR(1)      NOT NULL DEFAULT '0',
    create_time    TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ssa PRIMARY KEY (id)
);
CREATE INDEX idx_ssa_user ON t_shipping_address (user_id);

ALTER TABLE t_refund_order ADD goods_disposition VARCHAR(20);
COMMENT ON COLUMN t_refund_order.goods_disposition IS '已发货退款商品去向：LOST-商品丢失/无法回收，RECOVERED-商品已全部回收';

ALTER TABLE t_bill_reconcile_discrepancy ADD local_biz_no VARCHAR(50);
ALTER TABLE t_bill_reconcile_discrepancy ADD local_ledger_no VARCHAR(64);
ALTER TABLE t_bill_reconcile_discrepancy ADD local_serial_no VARCHAR(64);
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_biz_no IS '平台侧业务单号：支付为订单号，退款为退款单号';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_ledger_no IS '平台账本单号：支付为payment_no，退款为refund_no';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_serial_no IS '平台记录的渠道流水号：支付为channel_order_no；退款当前可为空';

CREATE INDEX idx_payment_order_channel_status_paid_time
    ON t_payment_order(channel, status, paid_time);
CREATE INDEX idx_refund_order_status_success_time
    ON t_refund_order(status, success_time);
-- 初始化完成后显式提交数据
COMMIT;
