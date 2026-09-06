-- =====================================================================
-- upgrade_payment_order_unique_fix.sql
-- 修复：DM8 下 uk_payment_channel_order(channel, channel_order_no) 组合唯一约束
--       将 (channel, NULL) 视为相等，导致任一渠道存在 channel_order_no 为 NULL 的
--       历史支付单后，该渠道所有新支付单（CREATED/PAYING 阶段 txn 为 NULL）插入
--       全部报"违反唯一性约束"，支付功能被完全阻塞。
-- 方案：删除普通组合唯一约束，改建函数唯一索引——channel_order_no 为 NULL 的行
--       用行 id 参与第一列保证行间互不冲突；非 NULL 行保持 (channel, txn) 唯一语义。
-- 适用：执行过旧版 payment_demo.sql 或 upgrade_trading_model_v2.sql 的存量库。
-- 日期：2026-09-06
-- =====================================================================

ALTER TABLE t_payment_order DROP CONSTRAINT uk_payment_channel_order;

CREATE UNIQUE INDEX uk_payment_channel_order ON t_payment_order (
  CASE WHEN channel_order_no IS NULL THEN 'ID:' || CAST(id AS VARCHAR(20)) ELSE channel END,
  CASE WHEN channel_order_no IS NULL THEN NULL ELSE channel_order_no END
);
