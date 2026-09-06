-- ===============================================================
-- Payment Demo DM8 完整初始化脚本（V3/V4/V5/V6 已整合，单一全量脚本，纯 DM8 语法）
-- 覆盖范围：支付渠道/应用、订单与多商品明细快照、支付流水、退款单与
--           用户分项退款申请、用户中心、找回密码申请、商品库存与可售状态、
--           库存操作幂等日志、微信交易账单上传式对账（导入批次/账单流水/对账差异）。
-- 本脚本为破坏性重建：先按依赖顺序 DROP 再 CREATE，并写入初始化数据。
-- 已整合：upgrade_admin_auth_v3.sql / upgrade_stock_audit_v4.sql /
--         upgrade_stock_import_v5.sql / upgrade_stock_import_v6.sql。
-- 注意：上述增量脚本的最终结构已经固化到本全量脚本中，切勿在本脚本后再次执行，
--       否则会产生重复建表、重复加列或重复建索引错误。
-- 已合并原增量脚本 upgrade_user_cart_order_refund_stock.sql 的全部内容
-- （t_user / t_order_item / t_refund_apply / t_refund_apply_item /
--   t_stock_operation_log，以及 t_product 的 stock / product_status 列），
-- 全新初始化无需再执行任何增量升级脚本；原增量脚本中“旧库历史订单明细
-- 迁移”语句仅服务于已存在数据的旧库，不属于全量重建范围，故不纳入。
-- 对账功能：旧自动拉单式对账三表（t_reconciliation_batch/detail/discrepancy）
-- 已废弃，仅保留 DROP 守卫块清理遗留库，不再重建；新对账为“微信交易账单
-- 上传式对账”，使用 t_bill_import / t_bill_record /
-- t_bill_reconcile_discrepancy 三张表。
-- 运行方式：docker compose 启动 DM8 后执行 env/scripts/dm8/init-dm8-sql.sh，
--           该脚本会按文件名顺序执行 env/sql/dm8 下的 *.sql。
-- 默认连接用户：SYSDBA；默认 schema：SYSDBA。
-- ===============================================================

-- ----------------------------
-- Drop tables in dependency-safe order.
-- 直接 DDL（非 PL/SQL），每条 ; 结尾，DM Manager 不会拆散。
-- 首次执行时表不存在会报错，请在 DM Manager 设置"出错时继续"：
--   工具 > 选项 > SQL执行 > 出错时 继续。
-- ----------------------------
DROP TABLE t_order_shipment CASCADE;
DROP TABLE t_inventory_transaction CASCADE;
DROP TABLE t_stock_import CASCADE;
DROP TABLE t_inventory_reservation CASCADE;
DROP TABLE t_payment_order CASCADE;
DROP TABLE t_refund_item CASCADE;
DROP TABLE t_refund_order CASCADE;
DROP TABLE t_schema_migration CASCADE;
DROP TABLE t_refund_apply_item CASCADE;
DROP TABLE t_refund_apply CASCADE;
DROP TABLE t_stock_operation_log CASCADE;
DROP TABLE t_order_item CASCADE;
DROP TABLE t_password_reset_request CASCADE;
DROP TABLE t_user CASCADE;
DROP TABLE t_refund_info CASCADE;
DROP TABLE t_payment_info CASCADE;
DROP TABLE t_order_info CASCADE;
DROP TABLE t_payment_app CASCADE;
DROP TABLE t_product CASCADE;
DROP TABLE t_payment_channel CASCADE;
DROP TABLE t_reconciliation_discrepancy CASCADE;
DROP TABLE t_reconciliation_detail CASCADE;
DROP TABLE t_reconciliation_batch CASCADE;
DROP TABLE t_bill_reconcile_discrepancy CASCADE;
DROP TABLE t_bill_record CASCADE;
DROP TABLE t_bill_import CASCADE;

-- ----------------------------
-- t_payment_channel 支付渠道配置表
-- ----------------------------
CREATE TABLE t_payment_channel (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  channel_name VARCHAR(64) NOT NULL,
  channel_code VARCHAR(32) NOT NULL,
  channel_status VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
  channel_desc VARCHAR(255),
  config_params CLOB,
  appid VARCHAR(64),
  mch_id VARCHAR(32),
  mch_serial_no VARCHAR(64),
  private_key CLOB,
  api_v3_key VARCHAR(128),
  partner_key VARCHAR(128),
  alipay_app_id VARCHAR(64),
  seller_id VARCHAR(64),
  merchant_private_key CLOB,
  alipay_public_key CLOB,
  sort_order INT DEFAULT 0 NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_payment_channel PRIMARY KEY (id),
  CONSTRAINT uk_channel_code UNIQUE (channel_code)
);

COMMENT ON TABLE t_payment_channel IS '支付渠道配置表：保存渠道级公共参数与商户信息（含商户私钥内容）';
COMMENT ON COLUMN t_payment_channel.id IS '支付渠道ID';
COMMENT ON COLUMN t_payment_channel.channel_name IS '渠道名称，例如微信支付、支付宝';
COMMENT ON COLUMN t_payment_channel.channel_code IS '渠道编码：WXPAY、ALIPAY';
COMMENT ON COLUMN t_payment_channel.channel_status IS '渠道状态：ENABLED-启用，DISABLED-禁用';
COMMENT ON COLUMN t_payment_channel.channel_desc IS '渠道描述';
COMMENT ON COLUMN t_payment_channel.config_params IS '渠道公共参数JSON字符串，例如domain、gatewayUrl、contentKey、notifyUrl、returnUrl';
COMMENT ON COLUMN t_payment_channel.appid IS '微信appid（公众号/小程序/APP），WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.mch_id IS '微信商户号，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.mch_serial_no IS '微信商户API证书序列号，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.private_key IS '微信商户私钥PEM内容（\n转义或真实换行，代码侧归一化），替代apiclient_key.pem文件';
COMMENT ON COLUMN t_payment_channel.api_v3_key IS '微信APIv3密钥，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.partner_key IS '微信APIv2密钥，WXPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.alipay_app_id IS '支付宝应用ID，ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.seller_id IS '支付宝卖家PID，ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.merchant_private_key IS '支付宝应用私钥（base64），ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.alipay_public_key IS '支付宝公钥（base64），ALIPAY渠道使用';
COMMENT ON COLUMN t_payment_channel.sort_order IS '排序号';
COMMENT ON COLUMN t_payment_channel.create_time IS '创建时间';
COMMENT ON COLUMN t_payment_channel.update_time IS '更新时间';

CREATE INDEX idx_channel_status_sort ON t_payment_channel(channel_status, sort_order);

CREATE OR REPLACE TRIGGER trg_payment_channel_uptime
BEFORE UPDATE ON t_payment_channel
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_payment_app 支付应用配置表
-- ----------------------------
CREATE TABLE t_payment_app (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  app_name VARCHAR(64) NOT NULL,
  app_code VARCHAR(64) NOT NULL,
  app_status VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
  channel_id BIGINT NOT NULL,
  app_desc VARCHAR(255),
  sort_order INT DEFAULT 0 NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_payment_app PRIMARY KEY (id),
  CONSTRAINT uk_app_code UNIQUE (app_code),
  CONSTRAINT fk_payment_app_channel FOREIGN KEY (channel_id) REFERENCES t_payment_channel(id)
);

COMMENT ON TABLE t_payment_app IS '支付应用配置表：仅保存应用业务信息，商户密钥统一保存在渠道表';
COMMENT ON COLUMN t_payment_app.id IS '支付应用ID';
COMMENT ON COLUMN t_payment_app.app_name IS '应用名称';
COMMENT ON COLUMN t_payment_app.app_code IS '应用编码';
COMMENT ON COLUMN t_payment_app.app_status IS '应用状态：ENABLED-启用，DISABLED-禁用';
COMMENT ON COLUMN t_payment_app.channel_id IS '关联支付渠道ID';
COMMENT ON COLUMN t_payment_app.app_desc IS '应用描述';
COMMENT ON COLUMN t_payment_app.sort_order IS '排序号';
COMMENT ON COLUMN t_payment_app.create_time IS '创建时间';
COMMENT ON COLUMN t_payment_app.update_time IS '更新时间';

CREATE INDEX idx_app_channel_status_sort ON t_payment_app(channel_id, app_status, sort_order);

CREATE OR REPLACE TRIGGER trg_payment_app_uptime
BEFORE UPDATE ON t_payment_app
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
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
CREATE TABLE t_order_info (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  title VARCHAR(256),
  order_no VARCHAR(50) NOT NULL,
  user_id BIGINT,
  product_id BIGINT NOT NULL,
  total_fee INT NOT NULL,
  code_url VARCHAR(512),
  legacy_status VARCHAR(30),
  order_status VARCHAR(32) DEFAULT 'WAIT_PAY' NOT NULL,
  pay_status VARCHAR(32) DEFAULT 'UNPAID' NOT NULL,
  fulfillment_status VARCHAR(32) DEFAULT 'WAIT_SHIP' NOT NULL,
  refund_status VARCHAR(32) DEFAULT 'NONE' NOT NULL,
  paid_amount INT DEFAULT 0 NOT NULL,
  refund_frozen_amount INT DEFAULT 0 NOT NULL,
  refunded_amount INT DEFAULT 0 NOT NULL,
  expire_time TIMESTAMP,
  paid_time TIMESTAMP,
  receiver_name VARCHAR(64),
  receiver_phone VARCHAR(32),
  receiver_address VARCHAR(255),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  payment_type VARCHAR(20) NOT NULL,
  payment_app_id BIGINT,
  payment_channel_code VARCHAR(32),
  version INT DEFAULT 0 NOT NULL,
  CONSTRAINT pk_order_info PRIMARY KEY (id),
  CONSTRAINT uk_order_no UNIQUE (order_no)
);

COMMENT ON TABLE t_order_info IS '订单表：V2 四维状态（交易/支付/履约/退款），乐观锁 version，退款冻结三层防线之订单层';
COMMENT ON COLUMN t_order_info.id IS '订单id';
COMMENT ON COLUMN t_order_info.title IS '订单标题';
COMMENT ON COLUMN t_order_info.order_no IS '商户订单编号';
COMMENT ON COLUMN t_order_info.user_id IS '用户id';
COMMENT ON COLUMN t_order_info.product_id IS '主商品id（兼容旧字段，真实商品组成以 t_order_item 为准）';
COMMENT ON COLUMN t_order_info.total_fee IS '应付金额(分)';
COMMENT ON COLUMN t_order_info.code_url IS '订单二维码连接';
COMMENT ON COLUMN t_order_info.legacy_status IS 'V1 旧状态值，仅供审计/回滚对照';
COMMENT ON COLUMN t_order_info.order_status IS '交易状态：WAIT_PAY-待支付，ACTIVE-有效，CLOSED-已关闭，COMPLETED-已完成';
COMMENT ON COLUMN t_order_info.pay_status IS '支付状态：UNPAID-未支付，PAID-已支付';
COMMENT ON COLUMN t_order_info.fulfillment_status IS '履约状态：WAIT_SHIP-待发货，SHIPPED-已发货，RECEIVED-已收货，CANCELLED-已取消';
COMMENT ON COLUMN t_order_info.refund_status IS '退款汇总状态：NONE-无退款，REFUNDING-退款中，PARTIAL_REFUNDED-部分退款，FULL_REFUNDED-全额退款';
COMMENT ON COLUMN t_order_info.paid_amount IS '有效实付金额(分)';
COMMENT ON COLUMN t_order_info.refund_frozen_amount IS '退款申请冻结金额(分)：已占用但尚未完成';
COMMENT ON COLUMN t_order_info.refunded_amount IS '已成功退款金额(分)';
COMMENT ON COLUMN t_order_info.expire_time IS '订单过期时间（超时关单/库存预占释放边界）';
COMMENT ON COLUMN t_order_info.paid_time IS '支付成功时间';
COMMENT ON COLUMN t_order_info.receiver_name IS '收货人姓名（物流模拟）';
COMMENT ON COLUMN t_order_info.receiver_phone IS '收货人电话（物流模拟）';
COMMENT ON COLUMN t_order_info.receiver_address IS '收货地址（物流模拟）';
COMMENT ON COLUMN t_order_info.payment_type IS '支付类型：支付宝、微信';
COMMENT ON COLUMN t_order_info.payment_app_id IS '支付应用ID';
COMMENT ON COLUMN t_order_info.payment_channel_code IS '支付渠道编码：WXPAY、ALIPAY';
COMMENT ON COLUMN t_order_info.version IS '乐观锁版本号';

CREATE INDEX idx_product_status_pay_type ON t_order_info(product_id, order_status, payment_type);
CREATE INDEX idx_product_payment_status_time ON t_order_info(product_id, payment_type, order_status, create_time);
CREATE INDEX idx_payment_app ON t_order_info(payment_app_id, create_time);
CREATE INDEX idx_order_lifecycle ON t_order_info(user_id, order_status, pay_status, fulfillment_status);

CREATE OR REPLACE TRIGGER trg_order_info_uptime
BEFORE UPDATE ON t_order_info
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_payment_info 支付流水表
-- ----------------------------
CREATE TABLE t_payment_info (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  transaction_id VARCHAR(50),
  payment_type VARCHAR(20) NOT NULL,
  trade_type VARCHAR(20),
  trade_state VARCHAR(50),
  payer_total INT,
  content CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_payment_info PRIMARY KEY (id),
  CONSTRAINT uk_order_payment_type UNIQUE (order_no, payment_type),
  CONSTRAINT uk_transaction_id_payment_type UNIQUE (transaction_id, payment_type)
);

COMMENT ON TABLE t_payment_info IS '支付流水表：双重幂等控制';
COMMENT ON COLUMN t_payment_info.id IS '支付记录id';
COMMENT ON COLUMN t_payment_info.order_no IS '商户订单编号';
COMMENT ON COLUMN t_payment_info.transaction_id IS '支付系统交易编号';
COMMENT ON COLUMN t_payment_info.payment_type IS '支付类型';
COMMENT ON COLUMN t_payment_info.trade_type IS '交易类型';
COMMENT ON COLUMN t_payment_info.trade_state IS '交易状态';
COMMENT ON COLUMN t_payment_info.payer_total IS '支付金额(分)';
COMMENT ON COLUMN t_payment_info.content IS '通知参数';

CREATE INDEX idx_payment_order_no ON t_payment_info(order_no);

CREATE OR REPLACE TRIGGER trg_payment_info_uptime
BEFORE UPDATE ON t_payment_info
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_product 商品表
-- ----------------------------
CREATE TABLE t_product (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  title VARCHAR(20),
  price INT DEFAULT 1 NOT NULL,
  available_stock INT DEFAULT 100 NOT NULL,
  locked_stock INT DEFAULT 0 NOT NULL,
  sold_stock INT DEFAULT 0 NOT NULL,
  product_status VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_product PRIMARY KEY (id)
);

COMMENT ON TABLE t_product IS '商品表：库存单元（本系统无独立 SKU 维度，product_id 兼作文档模型中的 sku_id）';
COMMENT ON COLUMN t_product.id IS '商品id';
COMMENT ON COLUMN t_product.title IS '商品名称';
COMMENT ON COLUMN t_product.price IS '售价(分)';
COMMENT ON COLUMN t_product.available_stock IS '可用库存：可被下单预占的数量';
COMMENT ON COLUMN t_product.locked_stock IS '锁定库存：下单预占未结转的数量（支付后保持锁定，确认收货结转已售/退款释放）';
COMMENT ON COLUMN t_product.sold_stock IS '已售库存：确认收货结转的数量（已售退款回补时扣减）';
COMMENT ON COLUMN t_product.product_status IS '商品状态：ENABLED/DISABLED';

CREATE OR REPLACE TRIGGER trg_product_uptime
BEFORE UPDATE ON t_product
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_refund_info 退款表
-- ----------------------------
CREATE TABLE t_refund_info (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  refund_no VARCHAR(50) NOT NULL,
  refund_id VARCHAR(50),
  total_fee INT,
  refund INT,
  reason VARCHAR(50),
  approval_status VARCHAR(20),
  approve_remark VARCHAR(255),
  approved_time TIMESTAMP,
  refund_status VARCHAR(30),
  content_return CLOB,
  content_notify CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_refund_info PRIMARY KEY (id),
  CONSTRAINT uk_refund_no UNIQUE (refund_no),
  CONSTRAINT uk_refund_id UNIQUE (refund_id)
);

COMMENT ON TABLE t_refund_info IS '退款表：双重幂等控制';
COMMENT ON COLUMN t_refund_info.id IS '退款单id';
COMMENT ON COLUMN t_refund_info.order_no IS '商户订单编号';
COMMENT ON COLUMN t_refund_info.refund_no IS '商户退款单编号';
COMMENT ON COLUMN t_refund_info.refund_id IS '支付系统退款单号';
COMMENT ON COLUMN t_refund_info.total_fee IS '原订单金额(分)';
COMMENT ON COLUMN t_refund_info.refund IS '退款金额(分)';
COMMENT ON COLUMN t_refund_info.reason IS '退款原因';
COMMENT ON COLUMN t_refund_info.approval_status IS '审核状态';
COMMENT ON COLUMN t_refund_info.approve_remark IS '审核备注';
COMMENT ON COLUMN t_refund_info.approved_time IS '审核时间';
COMMENT ON COLUMN t_refund_info.refund_status IS '退款状态';
COMMENT ON COLUMN t_refund_info.content_return IS '申请退款返回参数';
COMMENT ON COLUMN t_refund_info.content_notify IS '退款结果通知参数';

CREATE INDEX idx_refund_order_approval_status ON t_refund_info(order_no, approval_status);
CREATE INDEX idx_refund_info_order_refund_status ON t_refund_info(order_no, refund_status);

CREATE OR REPLACE TRIGGER trg_refund_info_uptime
BEFORE UPDATE ON t_refund_info
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/


-- ----------------------------
-- t_user 用户中心
-- ----------------------------
CREATE TABLE t_user (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  username VARCHAR(32) NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  password_salt VARCHAR(64) NOT NULL,
  role VARCHAR(32) DEFAULT 'ROLE_USER' NOT NULL,
  user_status VARCHAR(16) DEFAULT 'ENABLED' NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
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
CREATE TABLE t_password_reset_request (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  username VARCHAR(32) NOT NULL,
  remark VARCHAR(255),
  status VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
  admin_remark VARCHAR(255),
  handled_by BIGINT,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_password_reset_request PRIMARY KEY (id)
);

COMMENT ON TABLE t_password_reset_request IS '找回密码申请表：用户提交申请，管理员处理后生成一次性随机密码';
COMMENT ON COLUMN t_password_reset_request.username IS '申请重置的登录用户名';
COMMENT ON COLUMN t_password_reset_request.remark IS '用户申请备注（联系方式/说明）';
COMMENT ON COLUMN t_password_reset_request.status IS '申请状态：PENDING-待处理，HANDLED-已处理，REJECTED-已拒绝';
COMMENT ON COLUMN t_password_reset_request.admin_remark IS '管理员处理备注';
COMMENT ON COLUMN t_password_reset_request.handled_by IS '处理的管理员用户ID';
COMMENT ON COLUMN t_password_reset_request.create_time IS '申请时间';
COMMENT ON COLUMN t_password_reset_request.update_time IS '更新时间';

CREATE INDEX idx_password_reset_status_time ON t_password_reset_request(status, create_time);

CREATE OR REPLACE TRIGGER trg_password_reset_uptime
BEFORE UPDATE ON t_password_reset_request
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_order_item 订单商品快照明细（V2：成交快照 + 退款防线字段）
-- ----------------------------
CREATE TABLE t_order_item (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  product_id BIGINT NOT NULL,
  product_title VARCHAR(256),
  unit_price INT NOT NULL,
  quantity INT NOT NULL,
  deal_unit_amount INT,
  original_total_amount INT,
  discount_amount INT DEFAULT 0 NOT NULL,
  pay_amount INT,
  refunded_qty INT DEFAULT 0 NOT NULL,
  refund_frozen_qty INT DEFAULT 0 NOT NULL,
  refund_frozen_amount INT DEFAULT 0 NOT NULL,
  refunded_amount INT DEFAULT 0 NOT NULL,
  restocked_qty INT DEFAULT 0 NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_order_item PRIMARY KEY (id)
);
CREATE INDEX idx_order_item_order_no ON t_order_item(order_no);
CREATE INDEX idx_order_item_product_id ON t_order_item(product_id);

COMMENT ON TABLE t_order_item IS '订单商品明细：下单时的不可变成交快照，退款数量/金额/补库存防线的明细层';
COMMENT ON COLUMN t_order_item.id IS '订单明细id';
COMMENT ON COLUMN t_order_item.order_id IS '订单id';
COMMENT ON COLUMN t_order_item.order_no IS '商户订单编号';
COMMENT ON COLUMN t_order_item.product_id IS '商品id（兼作库存单元/sku_id）';
COMMENT ON COLUMN t_order_item.product_title IS '商品名称快照';
COMMENT ON COLUMN t_order_item.unit_price IS '成交单价(分)快照';
COMMENT ON COLUMN t_order_item.quantity IS '购买数量';
COMMENT ON COLUMN t_order_item.deal_unit_amount IS '成交单价(分)快照（与 unit_price 一致，结构预留优惠分摊）';
COMMENT ON COLUMN t_order_item.original_total_amount IS '原始小计(分)=unit_price*quantity';
COMMENT ON COLUMN t_order_item.discount_amount IS '分摊优惠金额(分)，当前恒为 0，结构预留';
COMMENT ON COLUMN t_order_item.pay_amount IS '该订单行实际承担支付金额(分)，退款资金上限';
COMMENT ON COLUMN t_order_item.refunded_qty IS '已退款数量';
COMMENT ON COLUMN t_order_item.refund_frozen_qty IS '退款申请冻结数量';
COMMENT ON COLUMN t_order_item.refund_frozen_amount IS '退款申请冻结金额(分)';
COMMENT ON COLUMN t_order_item.refunded_amount IS '已退款金额(分)';
COMMENT ON COLUMN t_order_item.restocked_qty IS '已补回库存数量';

-- ----------------------------
-- t_refund_order / t_refund_item 退款单与退款明细（V2 资金退款主线）
-- status: APPLYING/APPROVED/REJECTED/CANCELLED/REFUNDING/SUCCESS/FAILED
-- refund_type: CANCEL_BEFORE_SHIP/RETURN_AND_REFUND/REFUND_ONLY/
--              PRICE_ADJUSTMENT/DUPLICATE_PAYMENT/LATE_PAYMENT
-- ----------------------------
CREATE TABLE t_refund_order (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  refund_no VARCHAR(50) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  user_id BIGINT,
  refund_type VARCHAR(32) DEFAULT 'REFUND_ONLY' NOT NULL,
  payment_no VARCHAR(64),
  refund_amount INT NOT NULL,
  reason VARCHAR(255) NOT NULL,
  status VARCHAR(32) NOT NULL,
  legacy_apply_status VARCHAR(20),
  apply_type VARCHAR(20) DEFAULT 'USER' NOT NULL,
  admin_remark VARCHAR(255),
  accepted_time TIMESTAMP,
  success_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_refund_order PRIMARY KEY (id),
  CONSTRAINT uk_refund_order_no UNIQUE (refund_no)
);
CREATE INDEX idx_refund_order_order_no ON t_refund_order(order_no);
CREATE INDEX idx_refund_order_user_id ON t_refund_order(user_id);
CREATE INDEX idx_refund_order_status ON t_refund_order(status);

COMMENT ON TABLE t_refund_order IS '退款单：资金退款主线，由 V1 t_refund_apply 演进；异常支付冲正（重复/晚到）也落本表但不占售后额度';
COMMENT ON COLUMN t_refund_order.id IS '退款单id';
COMMENT ON COLUMN t_refund_order.refund_no IS '商户退款单编号';
COMMENT ON COLUMN t_refund_order.order_no IS '商户订单编号';
COMMENT ON COLUMN t_refund_order.user_id IS '申请人用户id';
COMMENT ON COLUMN t_refund_order.refund_type IS '退款类型：CANCEL_BEFORE_SHIP-未发货取消，RETURN_AND_REFUND-退货退款，REFUND_ONLY-仅退款，PRICE_ADJUSTMENT-差价退款，DUPLICATE_PAYMENT-重复支付退款，LATE_PAYMENT-晚到支付退款';
COMMENT ON COLUMN t_refund_order.payment_no IS '退款来源支付单编号（原路退回依据）';
COMMENT ON COLUMN t_refund_order.refund_amount IS '退款金额(分)，服务端按订单快照计算';
COMMENT ON COLUMN t_refund_order.reason IS '退款原因';
COMMENT ON COLUMN t_refund_order.status IS '退款状态：APPLYING-申请中，APPROVED-已受理，REJECTED-已拒绝，CANCELLED-已撤回，REFUNDING-渠道退款中，SUCCESS-退款成功，FAILED-退款失败';
COMMENT ON COLUMN t_refund_order.legacy_apply_status IS 'V1 旧申请状态值，仅供审计/回滚对照';
COMMENT ON COLUMN t_refund_order.apply_type IS '申请来源：USER-用户申请，ADMIN-管理员，SYSTEM-系统自动';
COMMENT ON COLUMN t_refund_order.admin_remark IS '管理员备注';
COMMENT ON COLUMN t_refund_order.accepted_time IS '受理时间';
COMMENT ON COLUMN t_refund_order.success_time IS '退款成功时间';

CREATE TABLE t_refund_item (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  refund_order_id BIGINT NOT NULL,
  refund_no VARCHAR(50) NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  unit_price INT NOT NULL,
  refund_qty INT NOT NULL,
  refund_amount INT NOT NULL,
  restock_qty INT DEFAULT 0 NOT NULL,
  legacy_stock_returned INT DEFAULT 0 NOT NULL,
  status VARCHAR(32),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_refund_item PRIMARY KEY (id),
  CONSTRAINT uk_refund_item_order_item UNIQUE (refund_no, order_item_id)
);
CREATE INDEX idx_refund_item_order_item ON t_refund_item(order_item_id);
CREATE INDEX idx_refund_item_refund_no ON t_refund_item(refund_no);

COMMENT ON TABLE t_refund_item IS '退款明细：退款数量/金额精确到订单行；restock_qty 记录实际补库存数量（是否补库存取决于退款类型与履约状态）';
COMMENT ON COLUMN t_refund_item.id IS '退款明细id';
COMMENT ON COLUMN t_refund_item.refund_order_id IS '退款单id';
COMMENT ON COLUMN t_refund_item.refund_no IS '商户退款单编号';
COMMENT ON COLUMN t_refund_item.order_item_id IS '订单明细id';
COMMENT ON COLUMN t_refund_item.product_id IS '商品id';
COMMENT ON COLUMN t_refund_item.unit_price IS '成交单价(分)快照';
COMMENT ON COLUMN t_refund_item.refund_qty IS '本次退款数量';
COMMENT ON COLUMN t_refund_item.refund_amount IS '本次退款金额(分)';
COMMENT ON COLUMN t_refund_item.restock_qty IS '已补回库存数量';
COMMENT ON COLUMN t_refund_item.legacy_stock_returned IS 'V1 旧已回补库存数，仅供审计';
COMMENT ON COLUMN t_refund_item.status IS '明细状态（随退款单主状态流转）';

-- ----------------------------
-- t_stock_operation_log MQ 幂等/死信人工重放
-- ----------------------------
CREATE TABLE t_stock_operation_log (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  biz_no VARCHAR(100) NOT NULL,
  order_no VARCHAR(50),
  refund_no VARCHAR(50),
  operation_type VARCHAR(20) NOT NULL,
  operation_status VARCHAR(30) NOT NULL,
  retry_count INT DEFAULT 0 NOT NULL,
  payload CLOB,
  error_message VARCHAR(1000),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_stock_operation_log PRIMARY KEY (id),
  CONSTRAINT uk_stock_operation_biz UNIQUE (biz_no)
);
CREATE INDEX idx_stock_operation_order_no ON t_stock_operation_log(order_no);
CREATE INDEX idx_stock_operation_refund_no ON t_stock_operation_log(refund_no);
CREATE INDEX idx_stock_operation_status ON t_stock_operation_log(operation_status);

COMMENT ON TABLE t_stock_operation_log IS '库存操作日志（V2 起交易链路不再写入，仅保留历史审计与 MQ 幂等记录结构）';

-- ----------------------------
-- t_payment_order 支付单（V2：本地订单 1:N 渠道支付尝试）
-- 一个业务订单允许多次支付尝试，但只允许一笔有效成交支付（SUCCESS）；
-- 其余成功支付进入 DUPLICATE_PAYMENT/LATE_PAYMENT 自动原路退款。
-- ----------------------------
CREATE TABLE t_payment_order (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  payment_no VARCHAR(64) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  channel VARCHAR(32) NOT NULL,
  channel_order_no VARCHAR(128),
  code_url VARCHAR(512),
  request_amount INT NOT NULL,
  paid_amount INT DEFAULT 0 NOT NULL,
  refund_frozen_amount INT DEFAULT 0 NOT NULL,
  refunded_amount INT DEFAULT 0 NOT NULL,
  status VARCHAR(32) DEFAULT 'CREATED' NOT NULL,
  expire_time TIMESTAMP,
  paid_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_payment_order PRIMARY KEY (id),
  CONSTRAINT uk_payment_order_no UNIQUE (payment_no)
);
-- 渠道单号唯一约束（函数唯一索引）：channel_order_no 为 NULL（支付单 CREATED/PAYING 阶段未获得渠道单号）的行
-- 不参与唯一性判定；DM8 对含 NULL 列的组合唯一约束视为相等，普通 UNIQUE 会阻塞同渠道后续 NULL 支付单。
CREATE UNIQUE INDEX uk_payment_channel_order ON t_payment_order (
  CASE WHEN channel_order_no IS NULL THEN 'ID:' || CAST(id AS VARCHAR(20)) ELSE channel END,
  CASE WHEN channel_order_no IS NULL THEN NULL ELSE channel_order_no END
);
CREATE INDEX idx_payment_order_order_no ON t_payment_order(order_no);
CREATE INDEX idx_payment_order_status ON t_payment_order(order_no, status);

COMMENT ON TABLE t_payment_order IS '支付单：一次支付渠道尝试；库存只属于业务订单，不属于支付单；渠道资金防线（累计退款不超过实付）落在本表';
COMMENT ON COLUMN t_payment_order.id IS '支付单id';
COMMENT ON COLUMN t_payment_order.payment_no IS '商户支付单编号（每次发起支付生成）';
COMMENT ON COLUMN t_payment_order.order_no IS '商户订单编号';
COMMENT ON COLUMN t_payment_order.channel IS '支付渠道：WXPAY、ALIPAY';
COMMENT ON COLUMN t_payment_order.channel_order_no IS '渠道侧交易号（微信 transaction_id / 支付宝 trade_no）';
COMMENT ON COLUMN t_payment_order.code_url IS '支付二维码连接（NATIVE）';
COMMENT ON COLUMN t_payment_order.request_amount IS '请求支付金额(分)';
COMMENT ON COLUMN t_payment_order.paid_amount IS '实际支付金额(分)';
COMMENT ON COLUMN t_payment_order.refund_frozen_amount IS '渠道退款冻结金额(分)';
COMMENT ON COLUMN t_payment_order.refunded_amount IS '渠道累计已退款金额(分)';
COMMENT ON COLUMN t_payment_order.status IS '支付单状态：CREATED-已创建，PAYING-支付中，SUCCESS-支付成功（有效成交），CLOSED-已关闭';
COMMENT ON COLUMN t_payment_order.expire_time IS '支付单过期时间（必须早于等于业务订单过期时间）';
COMMENT ON COLUMN t_payment_order.paid_time IS '支付成功时间';

-- ----------------------------
-- t_inventory_reservation 库存预占（V2：未支付阶段防超卖）
-- 状态机：LOCKED → COMMITTED（支付成功）/ RELEASED（关单/取消）
-- ----------------------------
CREATE TABLE t_inventory_reservation (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  reservation_no VARCHAR(64) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  order_item_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  status VARCHAR(32) DEFAULT 'LOCKED' NOT NULL,
  expire_time TIMESTAMP,
  commit_time TIMESTAMP,
  release_time TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_inventory_reservation PRIMARY KEY (id),
  CONSTRAINT uk_reservation_no UNIQUE (reservation_no),
  CONSTRAINT uk_reservation_order_item UNIQUE (order_item_id)
);
CREATE INDEX idx_reservation_order_no ON t_inventory_reservation(order_no);
CREATE INDEX idx_reservation_status ON t_inventory_reservation(status, expire_time);

COMMENT ON TABLE t_inventory_reservation IS '库存预占：每条订单明细对应一条预占（1:1），锁定→提交/释放';
COMMENT ON COLUMN t_inventory_reservation.id IS '预占id';
COMMENT ON COLUMN t_inventory_reservation.reservation_no IS '预占编号';
COMMENT ON COLUMN t_inventory_reservation.order_no IS '商户订单编号';
COMMENT ON COLUMN t_inventory_reservation.order_item_id IS '订单明细id（1:1 唯一）';
COMMENT ON COLUMN t_inventory_reservation.product_id IS '商品id（库存单元）';
COMMENT ON COLUMN t_inventory_reservation.quantity IS '预占数量';
COMMENT ON COLUMN t_inventory_reservation.status IS '预占状态：LOCKED-锁定，COMMITTED-已提交（成交），RELEASED-已释放';
COMMENT ON COLUMN t_inventory_reservation.expire_time IS '预占过期时间（=订单过期时间）';
COMMENT ON COLUMN t_inventory_reservation.commit_time IS '提交时间（支付成功）';
COMMENT ON COLUMN t_inventory_reservation.release_time IS '释放时间（关单/取消）';

-- ----------------------------
-- t_inventory_transaction 库存流水（V2：审计账本 + 幂等）
-- UNIQUE(biz_no) 保证同一库存业务动作只执行一次
-- ----------------------------
CREATE TABLE t_inventory_transaction (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  order_no VARCHAR(50),
  order_item_id BIGINT,
  refund_no VARCHAR(50),
  product_id BIGINT NOT NULL,
  available_delta INT NOT NULL,
  locked_delta INT NOT NULL,
  sold_delta INT DEFAULT 0 NOT NULL,
  operation_status VARCHAR(16) DEFAULT 'SUCCESS' NOT NULL,
  error_message VARCHAR(500),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_inventory_transaction PRIMARY KEY (id),
  CONSTRAINT uk_inventory_biz_no UNIQUE (biz_no)
);
CREATE INDEX idx_inventory_tx_product ON t_inventory_transaction(product_id);
CREATE INDEX idx_inventory_tx_order_no ON t_inventory_transaction(order_no);
CREATE INDEX idx_inventory_tx_refund_no ON t_inventory_transaction(refund_no);
CREATE INDEX idx_inventory_tx_status_time ON t_inventory_transaction(operation_status, create_time);

COMMENT ON TABLE t_inventory_transaction IS '库存流水：每次预占/提交/释放/回补/手工调整落一条，biz_no 唯一保证幂等；失败申请也记录（不改变库存）';
COMMENT ON COLUMN t_inventory_transaction.id IS '流水id';
COMMENT ON COLUMN t_inventory_transaction.biz_no IS '幂等业务号：TYPE:orderNo:itemId 或 REFUND_RESTOCK:refundNo:itemId 或 MANUAL_ADJUST:productId:UUID';
COMMENT ON COLUMN t_inventory_transaction.biz_type IS '流水类型：ORDER_RESERVE-下单预占，ORDER_COMMIT-支付提交（数量不变），ORDER_SOLD-确认收货结转已售，ORDER_RELEASE-关单释放，REFUND_RESTOCK-退款回补，MANUAL_ADJUST-管理员手工调整';
COMMENT ON COLUMN t_inventory_transaction.order_no IS '关联订单号';
COMMENT ON COLUMN t_inventory_transaction.order_item_id IS '关联订单明细id';
COMMENT ON COLUMN t_inventory_transaction.refund_no IS '关联退款单号（回补时）';
COMMENT ON COLUMN t_inventory_transaction.product_id IS '商品id（库存单元）';
COMMENT ON COLUMN t_inventory_transaction.available_delta IS '可用库存变化量（失败申请为 0）';
COMMENT ON COLUMN t_inventory_transaction.locked_delta IS '锁定库存变化量';
COMMENT ON COLUMN t_inventory_transaction.sold_delta IS '已售库存变化量';
COMMENT ON COLUMN t_inventory_transaction.operation_status IS '操作状态：SUCCESS-已生效，FAILED-调整申请被拒绝（库存未变化）';
COMMENT ON COLUMN t_inventory_transaction.error_message IS '失败原因（FAILED 时记录申请调整量与拒绝原因）';

-- ----------------------------
-- t_stock_import Excel 批量库存导入记录（V5：导入暂存 + 确认入库；V6：文件存储地址）
-- 导入仅生成 PENDING 记录，管理员在维护页选择记录、核对/编辑明细后
-- 点“确认入库”才逐条执行库存调整；实际库存变化与失败原因审计于
-- t_inventory_transaction（MANUAL_ADJUST 流水），本表仅追踪导入批次状态，
-- 并持久化 Excel 文件的存储后端与地址（LOCAL 本地磁盘 / MINIO 对象存储）。
-- ----------------------------
CREATE TABLE t_stock_import (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  file_name VARCHAR(255),
  item_count INT NOT NULL,
  status VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
  items_json VARCHAR(4000) NOT NULL,
  storage_type VARCHAR(16) DEFAULT 'LOCAL' NOT NULL,
  file_path VARCHAR(512),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  confirm_time TIMESTAMP,
  CONSTRAINT pk_stock_import PRIMARY KEY (id)
);
COMMENT ON TABLE t_stock_import IS 'Excel 批量库存导入记录：导入暂存 PENDING，确认入库后 CONFIRMED；明细 JSON 存 items_json，执行审计在 t_inventory_transaction';
COMMENT ON COLUMN t_stock_import.file_name IS '导入的 Excel 文件名';
COMMENT ON COLUMN t_stock_import.item_count IS '导入明细条数';
COMMENT ON COLUMN t_stock_import.status IS 'PENDING-待确认，CONFIRMED-已入库（CAS 抢占防重复执行）';
COMMENT ON COLUMN t_stock_import.items_json IS '导入明细 JSON：[{"productId":13,"delta":50},...]（确认时以页面编辑后明细为准执行）';
COMMENT ON COLUMN t_stock_import.storage_type IS '文件存储后端：LOCAL-后端本地磁盘，MINIO-MinIO 对象存储（空按 LOCAL 兼容 V5）';
COMMENT ON COLUMN t_stock_import.file_path IS '文件地址：LOCAL 为文件绝对路径，MINIO 为对象键（stock-import/{id}.xlsx）';
COMMENT ON COLUMN t_stock_import.confirm_time IS '确认入库时间';

-- ----------------------------
-- t_order_shipment 物流单（V2：模拟物流商对接）
-- 状态机：SHIPPED → IN_TRANSIT → DELIVERED → RECEIVED（/CANCELLED）
-- ----------------------------
CREATE TABLE t_order_shipment (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  shipment_no VARCHAR(64) NOT NULL,
  order_no VARCHAR(50) NOT NULL,
  logistics_company VARCHAR(64) DEFAULT '模拟快递' NOT NULL,
  tracking_no VARCHAR(64) NOT NULL,
  status VARCHAR(32) DEFAULT 'SHIPPED' NOT NULL,
  shipped_time TIMESTAMP,
  in_transit_time TIMESTAMP,
  delivered_time TIMESTAMP,
  received_time TIMESTAMP,
  remark VARCHAR(255),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_order_shipment PRIMARY KEY (id),
  CONSTRAINT uk_shipment_no UNIQUE (shipment_no),
  CONSTRAINT uk_shipment_tracking_no UNIQUE (tracking_no)
);
CREATE INDEX idx_shipment_order_no ON t_order_shipment(order_no);

COMMENT ON TABLE t_order_shipment IS '物流单：通过 LogisticsProvider 接口对接（当前为模拟物流商），定时任务模拟运输推进，用户确认收货后履约完成';
COMMENT ON COLUMN t_order_shipment.id IS '物流单id';
COMMENT ON COLUMN t_order_shipment.shipment_no IS '物流单编号';
COMMENT ON COLUMN t_order_shipment.order_no IS '商户订单编号';
COMMENT ON COLUMN t_order_shipment.logistics_company IS '物流公司（模拟：模拟快递）';
COMMENT ON COLUMN t_order_shipment.tracking_no IS '运单号（模拟物流商生成）';
COMMENT ON COLUMN t_order_shipment.status IS '物流状态：SHIPPED-已发货，IN_TRANSIT-运输中，DELIVERED-已派送，RECEIVED-已收货，CANCELLED-已撤销';
COMMENT ON COLUMN t_order_shipment.shipped_time IS '发货时间';
COMMENT ON COLUMN t_order_shipment.in_transit_time IS '进入运输时间（模拟）';
COMMENT ON COLUMN t_order_shipment.delivered_time IS '派送送达时间（模拟）';
COMMENT ON COLUMN t_order_shipment.received_time IS '用户确认收货时间';
COMMENT ON COLUMN t_order_shipment.remark IS '备注';

-- ----------------------------
-- t_bill_import 账单导入批次表（上传式对账）
-- 幂等：同渠道同账单日同账单种类唯一、同文件内容(hash)唯一，重复上传不产生新批次。
-- 账单种类：ALL（含支付+退款+撤销行）/SUCCESS（仅支付成功行）/REFUND（仅退款行），
-- 同一账单日允许一份ALL，或一份SUCCESS+一份REFUND分别对账，由服务层交叉校验。
-- ----------------------------
CREATE TABLE t_bill_import (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  import_no VARCHAR(50) NOT NULL,
  channel_code VARCHAR(32) NOT NULL,
  bill_type VARCHAR(20) DEFAULT 'TRADE' NOT NULL,
  bill_kind VARCHAR(16) DEFAULT 'ALL' NOT NULL,
  bill_date VARCHAR(10) NOT NULL,
  file_name VARCHAR(255),
  file_hash VARCHAR(64) NOT NULL,
  total_record_count INT DEFAULT 0 NOT NULL,
  pay_record_count INT DEFAULT 0 NOT NULL,
  refund_record_count INT DEFAULT 0 NOT NULL,
  bad_line_count INT DEFAULT 0 NOT NULL,
  matched_count INT DEFAULT 0 NOT NULL,
  discrepancy_count INT DEFAULT 0 NOT NULL,
  status VARCHAR(20) DEFAULT 'IMPORTED' NOT NULL,
  reconcile_time TIMESTAMP,
  error_message VARCHAR(1000),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_bill_import PRIMARY KEY (id),
  CONSTRAINT uk_bill_import_no UNIQUE (import_no),
  CONSTRAINT uk_bill_import_channel_date UNIQUE (channel_code, bill_type, bill_date, bill_kind),
  CONSTRAINT uk_bill_import_file_hash UNIQUE (file_hash)
);

COMMENT ON TABLE t_bill_import IS '账单导入批次表：管理员上传渠道账单文件的导入与对账批次，同渠道同账单日同账单种类/同文件内容唯一（幂等）';
COMMENT ON COLUMN t_bill_import.id IS '导入批次ID';
COMMENT ON COLUMN t_bill_import.import_no IS '导入批次业务单号';
COMMENT ON COLUMN t_bill_import.channel_code IS '渠道编码：WXPAY（本期仅微信交易账单）';
COMMENT ON COLUMN t_bill_import.bill_type IS '账单类型：TRADE-交易账单';
COMMENT ON COLUMN t_bill_import.bill_kind IS '微信交易账单种类：ALL-全部账单(支付+退款)，SUCCESS-支付成功账单，REFUND-退款账单；对账范围按种类收窄';
COMMENT ON COLUMN t_bill_import.bill_date IS '账单日期，格式yyyy-MM-dd';
COMMENT ON COLUMN t_bill_import.file_name IS '上传的账单文件名';
COMMENT ON COLUMN t_bill_import.file_hash IS '账单文件内容SHA-256，用于同文件重复上传幂等';
COMMENT ON COLUMN t_bill_import.total_record_count IS '账单有效记录总笔数';
COMMENT ON COLUMN t_bill_import.pay_record_count IS '账单支付记录笔数';
COMMENT ON COLUMN t_bill_import.refund_record_count IS '账单退款记录笔数';
COMMENT ON COLUMN t_bill_import.bad_line_count IS '解析失败被跳过的坏行数';
COMMENT ON COLUMN t_bill_import.matched_count IS '对账匹配成功笔数';
COMMENT ON COLUMN t_bill_import.discrepancy_count IS '对账差异笔数';
COMMENT ON COLUMN t_bill_import.status IS '批次状态：IMPORTED-已导入，RECONCILED-已对账，FAILED-失败';
COMMENT ON COLUMN t_bill_import.reconcile_time IS '对账完成时间';
COMMENT ON COLUMN t_bill_import.error_message IS '失败原因';
COMMENT ON COLUMN t_bill_import.create_time IS '创建时间';
COMMENT ON COLUMN t_bill_import.update_time IS '更新时间';

CREATE INDEX idx_bill_import_bill_date ON t_bill_import(bill_date);
CREATE INDEX idx_bill_import_status ON t_bill_import(status);

CREATE OR REPLACE TRIGGER trg_bill_import_uptime
BEFORE UPDATE ON t_bill_import
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_bill_record 账单流水原始记录表
-- 保存账单中的支付行(PAY)与退款行(REFUND)原始数据，raw_line 保留原始行可追溯。
-- ----------------------------
CREATE TABLE t_bill_record (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  import_id BIGINT NOT NULL,
  channel_code VARCHAR(32) NOT NULL,
  bill_date VARCHAR(10) NOT NULL,
  record_type VARCHAR(16) NOT NULL,
  channel_serial_no VARCHAR(50) NOT NULL,
  biz_no VARCHAR(50) NOT NULL,
  trade_type VARCHAR(32),
  trade_status VARCHAR(32),
  total_amount INT,
  refund_amount INT,
  trade_time TIMESTAMP,
  refund_apply_time TIMESTAMP,
  refund_success_time TIMESTAMP,
  raw_line CLOB,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_bill_record PRIMARY KEY (id),
  CONSTRAINT uk_bill_record_serial UNIQUE (channel_code, bill_date, record_type, channel_serial_no)
);

COMMENT ON TABLE t_bill_record IS '账单流水原始记录表：账单支付行/退款行解析后的原始流水，raw_line 保留账单原文行';
COMMENT ON COLUMN t_bill_record.id IS '流水记录ID';
COMMENT ON COLUMN t_bill_record.import_id IS '所属账单导入批次ID';
COMMENT ON COLUMN t_bill_record.channel_code IS '渠道编码：WXPAY';
COMMENT ON COLUMN t_bill_record.bill_date IS '账单日期，格式yyyy-MM-dd';
COMMENT ON COLUMN t_bill_record.record_type IS '记录类型：PAY-支付，REFUND-退款';
COMMENT ON COLUMN t_bill_record.channel_serial_no IS '渠道流水号：支付为微信订单号，退款为微信退款单号';
COMMENT ON COLUMN t_bill_record.biz_no IS '业务单号：支付为商户订单号，退款为商户退款单号';
COMMENT ON COLUMN t_bill_record.trade_type IS '渠道交易类型，如JSAPI、NATIVE、REFUND';
COMMENT ON COLUMN t_bill_record.trade_status IS '渠道交易状态，如SUCCESS';
COMMENT ON COLUMN t_bill_record.total_amount IS '支付金额(分)，支付行取订单金额';
COMMENT ON COLUMN t_bill_record.refund_amount IS '退款金额(分)，退款行取退款金额绝对值';
COMMENT ON COLUMN t_bill_record.trade_time IS '交易时间';
COMMENT ON COLUMN t_bill_record.refund_apply_time IS '退款申请时间';
COMMENT ON COLUMN t_bill_record.refund_success_time IS '退款成功时间';
COMMENT ON COLUMN t_bill_record.raw_line IS '账单原始行文本';
COMMENT ON COLUMN t_bill_record.create_time IS '创建时间';
COMMENT ON COLUMN t_bill_record.update_time IS '更新时间';

CREATE INDEX idx_bill_record_import ON t_bill_record(import_id);
CREATE INDEX idx_bill_record_biz_no ON t_bill_record(biz_no);
CREATE INDEX idx_bill_record_type_date ON t_bill_record(record_type, bill_date);

CREATE OR REPLACE TRIGGER trg_bill_record_uptime
BEFORE UPDATE ON t_bill_record
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- t_bill_reconcile_discrepancy 账单对账差异单表
-- 幂等：同批次同差异类型同业务单号唯一，重复对账不产生重复差异。
-- ----------------------------
CREATE TABLE t_bill_reconcile_discrepancy (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  import_id BIGINT NOT NULL,
  bill_date VARCHAR(10) NOT NULL,
  biz_type VARCHAR(16) NOT NULL,
  discrepancy_type VARCHAR(40) NOT NULL,
  biz_no VARCHAR(50),
  channel_serial_no VARCHAR(50),
  channel_amount INT,
  local_amount INT,
  channel_status VARCHAR(50),
  local_status VARCHAR(50),
  status VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
  resolve_remark VARCHAR(512),
  resolved_time TIMESTAMP,
  resolved_by VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_bill_reconcile_discrepancy PRIMARY KEY (id),
  CONSTRAINT uk_bill_discrepancy_biz UNIQUE (import_id, discrepancy_type, biz_type, biz_no)
);

COMMENT ON TABLE t_bill_reconcile_discrepancy IS '账单对账差异单表：账单支付/退款记录与本地流水核对出的差异，同批次同类型同业务单号唯一（幂等）';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.id IS '差异单ID';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.import_id IS '所属账单导入批次ID';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.bill_date IS '账单日期，格式yyyy-MM-dd';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.biz_type IS '业务类型：PAY-支付核对，REFUND-退款核对';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.discrepancy_type IS '差异类型：PAY/REFUND × CHANNEL_ONLY/LOCAL_ONLY/AMOUNT_MISMATCH/STATUS_MISMATCH';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.biz_no IS '业务单号：商户订单号或商户退款单号';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.channel_serial_no IS '渠道流水号：微信订单号/退款单号';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.channel_amount IS '渠道侧金额(分)';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_amount IS '本地侧金额(分)';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.channel_status IS '渠道侧状态';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_status IS '本地侧状态';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.status IS '处理状态：OPEN-待处理，RESOLVED-已处理';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.resolve_remark IS '处理备注';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.resolved_time IS '处理时间';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.resolved_by IS '处理人';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.create_time IS '创建时间';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.update_time IS '更新时间';

CREATE INDEX idx_bill_discrepancy_import ON t_bill_reconcile_discrepancy(import_id);
CREATE INDEX idx_bill_discrepancy_status ON t_bill_reconcile_discrepancy(status);
CREATE INDEX idx_bill_discrepancy_bill_date ON t_bill_reconcile_discrepancy(bill_date);

CREATE OR REPLACE TRIGGER trg_bill_reconcile_discrepancy_uptime
BEFORE UPDATE ON t_bill_reconcile_discrepancy
FOR EACH ROW
BEGIN
  :NEW.update_time = CURRENT_TIMESTAMP;
END;
/

-- ----------------------------
-- Initial data：来源于现有 wxpay.properties / alipay-sandbox.properties，不使用 mock 数据
-- DELETE 清理守卫确保幂等：即使 DROP 未生效也不会违反唯一性约束。
-- ----------------------------
DELETE FROM t_payment_app WHERE app_code IN ('WXPAY_DEFAULT', 'ALIPAY_SANDBOX_DEFAULT');
DELETE FROM t_payment_channel WHERE channel_code IN ('WXPAY', 'ALIPAY');
DELETE FROM t_product WHERE title IN ('Java课程', '大数据课程', '前端课程', 'UI课程');

INSERT INTO t_payment_channel (channel_name, channel_code, channel_status, channel_desc, config_params, appid, mch_id, mch_serial_no, private_key, api_v3_key, partner_key, sort_order, create_time, update_time) VALUES
('微信支付', 'WXPAY', 'ENABLED', '微信支付渠道公共配置与商户信息', '{"domain":"https://api.mch.weixin.qq.com","notifyUrl":"https://5834-2409-8a50-3e25-e7f0-ecc6-e42-7b4c-1466.ngrok-free.app"}', 'wx74862e0dfcf69954', '1558950191', '34345964330B66427E0D3D28826C4993C77E631F', '-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDnSAKI8sea8p+d\nOBVPWlZmxqJfPbdhzZxdI5Kx1j5SJNZwXWtr43/giw38pwzSlBI+bubBcYlkFTI0\nguigMZO/yueb1mZChaY/JG1vsT02Ubj0xkVvBwKNbYS48NEpZhK61Mia09R4n1iH\n1vip9kt8J6Zrx+xIqwmuCNWigyivGrvY9AdevCNlNSVdHVOZUJiJ6UGtvVmgZb0u\nRTwBzfkjnwTgEcsrZMmF15nFubFsyJLyF/zY4NhrISc8H/rbjgleqa8ybYL26iTS\ngfPCXe4U9f8fNFF2bSA06GTiB2R93q2B0zHeUYrpgF4XOGlIAqH+Ea4Vn+aOj6I0\npduh03idAgMBAAECggEBAJ+4SB/hYd1szrPZhkXtwhtp87pIObtuLhzYMzdjGFjM\nHdctfMDeNHKSNU+U4bMPFOZO2kcfLF2Ukb5X5WSzuDBMZNRnJOmtuJiEhJsM0JQR\nreREhLDfK3EWAAFkNV4corSpu/vIbEP87zuoRsPBVnHgQ/rM7y1kCORKL5bycwcw\n5BI4xhULKAu14LEcDL3+xDJo39w+WCFlxuP+6Bs7+vIeavs+AC3TJkA4kg2nyWd3\nW07xPjHl64f17icqsFhuFZ+VuSf5CAgQGWDbC7BHqRkDStUDSiiUiFushouKCLdK\nMpA0x4ogb2ZwfZDRhZHiLNAGe4QovYCcXWBydzuT0WECgYEA828Bo1JAHE5kdnsO\nE9+enH/yMcOKTRnuYPiXsFXNvqofc5tZiXJmVE/+EKv7LFmtUA6qqKC7FDek8TpP\nSkfXmSDAgfM6AdzT0YoHH23FRVewnFMEYumtogXsXJTyI5siBSJp16s9Rn/YwESt\nJqjW5+9Ck1dkU+UJCZ4lOw4HeGkCgYEA8zho2BKQTh3P/xcFcoTcunVZpRayVkHM\ng8Ef6RGGo4vM1oshQLvXyPqCmhAIf6j71I9WPqUwjmeGyaR7Hir0dbgTCm2fJPFW\nlxAvgbCISxEPz10RYBcR2umMSlJLfZfhqv1CyfU4vfCTbdOimgsz2039E3oLTbzg\neDe/mdzu2BUCgYEAleKjf4wFLWiXMtxRrqrhXjrpRPrBDPgKbmqh+1DZfawB8YyV\ndKublg4qwNkjrgsJS2G8cleE2M3qIR1l9LaHaSFhZqH79WmigkIaYJ+V9zwm4hm7\neaun3TsIbXjIHmRGbiLiSIiHEgFl0/x1IHiU2fnXZCFLBNzg06ssAVCCCQECgYA1\n4BfxTONkOlxZgAr33BBcySPLWuS0EK0xvjTIVtaBIbWFDJqYEUPyQ/NsFwMa7B6k\nbf/HrqW71ZjYz7Np8k/mR5kIJVIsR71Lhw1O6AC4yBW9dDsmEtYkrLkjuWj5cAxP\n6PvDaqtf/4tYt5l8D+Ezwem+R7l7RcxfNNIfTf4mJQKBgE57dnRx+Ijx7VHjJvjl\nX2jB/VSVGpK5OADykmmZ/wvHPlQcyzd+5kAIoJhSuY48CFeI1DOogR2p01LEFQEL\nj4AI5FqOOQwRJvNmfoKcKwO36tSxSEGSM8POKOsa21PG/gvDpJjVFo2hn5QcMHWn\nz5SjsgA/1YbXejubdLxT/3pl\n-----END PRIVATE KEY-----', 'UDuLFDcmy5Eb6o0nTNZdu6ek4DDh4K8B', 'T6m9iK73b0kn9g5v426MKfHQH7X8rKwb', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_payment_channel (channel_name, channel_code, channel_status, channel_desc, config_params, alipay_app_id, seller_id, merchant_private_key, alipay_public_key, sort_order, create_time, update_time) VALUES
('支付宝', 'ALIPAY', 'ENABLED', '支付宝渠道公共配置与商户信息', '{"gatewayUrl":"https://openapi-sandbox.dl.alipaydev.com/gateway.do","contentKey":"GD8AS9VQy0hZROhMzOARQw==","returnUrl":"http://localhost:8083/#/success","notifyUrl":"http://arhi.nat300.top/api/ali-pay/trade/notify"}', '9021000136667568', '2088721034748965', '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', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 使用子查询获取渠道ID，避免硬编码 channel_id=1/2 在 IDENTITY 未重置时失效
-- 应用表不再保存商户参数（app_config 已移除），商户信息统一在渠道表
INSERT INTO t_payment_app (app_name, app_code, app_status, channel_id, app_desc, sort_order, create_time, update_time) VALUES
('微信支付默认应用', 'WXPAY_DEFAULT', 'ENABLED', (SELECT id FROM t_payment_channel WHERE channel_code='WXPAY'), '微信支付默认应用（商户参数见WXPAY渠道）', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_payment_app (app_name, app_code, app_status, channel_id, app_desc, sort_order, create_time, update_time) VALUES
('支付宝沙箱默认应用', 'ALIPAY_SANDBOX_DEFAULT', 'ENABLED', (SELECT id FROM t_payment_channel WHERE channel_code='ALIPAY'), '支付宝沙箱默认应用（商户参数见ALIPAY渠道）', 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time) VALUES ('Java课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');
INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time) VALUES ('大数据课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');
INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time) VALUES ('前端课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');
INSERT INTO t_product (title, price, available_stock, locked_stock, product_status, create_time, update_time) VALUES ('UI课程', 1, 100, 0, 'ENABLED', TIMESTAMP '2023-02-04 23:34:20', TIMESTAMP '2023-02-04 23:34:20');

-- =====================================================================
-- 本地消息表（事务性发件箱）：业务事务内落库 PENDING，事务提交后投递 MQ；
-- 发送者确认到达置 SENT；消费者监听器成功返回后回写 CONSUMED；重试超限置 FAILED 待人工补偿
-- =====================================================================
CREATE TABLE t_local_message (
  id BIGINT IDENTITY(1, 1) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_no VARCHAR(64) NOT NULL,
  message_content CLOB NOT NULL,
  status VARCHAR(16) DEFAULT 'PENDING' NOT NULL,
  retry_count INT DEFAULT 0 NOT NULL,
  next_retry_time TIMESTAMP NOT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_local_message PRIMARY KEY (id)
);
CREATE INDEX idx_local_message_scan ON t_local_message(status, next_retry_time);
CREATE INDEX idx_local_message_biz ON t_local_message(biz_type, biz_no);
COMMENT ON TABLE t_local_message IS '本地消息表（事务性发件箱）：延迟关单/退款同步消息可靠投递与消费回写';
COMMENT ON COLUMN t_local_message.biz_type IS '业务类型：ORDER_CLOSE-延迟关单，REFUND_SYNC-退款状态同步';
COMMENT ON COLUMN t_local_message.biz_no IS '业务单号：orderNo/refundNo';
COMMENT ON COLUMN t_local_message.message_content IS '消息内容JSON（与MQ消息体一致）';
COMMENT ON COLUMN t_local_message.status IS '状态：PENDING-待投递，SENT-已投递(发送者确认)，CONSUMED-已消费(消费成功回写)，FAILED-投递失败待人工补偿';
COMMENT ON COLUMN t_local_message.retry_count IS '投递重试次数';
COMMENT ON COLUMN t_local_message.next_retry_time IS '下次重试时间';

COMMIT;
