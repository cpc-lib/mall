-- =====================================================================
-- 本地消息表（事务性发件箱）升级脚本
-- 适用：已按旧版 payment_demo.sql 初始化的存量 DM8 库。
-- 新库直接执行 payment_demo.sql，无需本脚本。
-- 用途：延迟关单/退款状态同步消息可靠投递（发送者确认）+ 消费成功回写 CONSUMED。
-- 幂等性：重复执行会在建表处报"对象已存在"，属预期。
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
