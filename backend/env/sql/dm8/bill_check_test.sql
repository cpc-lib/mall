-- =============================================
-- 账账核对数据清理
-- 只清对账模块，不删除真实支付/退款业务数据
-- =============================================

-- 1. 先删除差异数据
DELETE FROM t_bill_reconcile_discrepancy;

-- 2. 再删除渠道账单解析流水
DELETE FROM t_bill_record;

-- 3. 最后删除导入批次
DELETE FROM t_bill_import;

COMMIT;