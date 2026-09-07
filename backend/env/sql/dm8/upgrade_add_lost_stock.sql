-- ============================================================================
-- 升级脚本：库存模型新增「丢失/货损」桶（仅退款未收货核销）
-- 适用：已部署的存量 DM8 库（全新建库直接跑 schema.sql 即可，无需本脚本）
-- 幂等：DM8 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报「列已存在」，属正常
-- 对应需求：仅退款（不退货）时货物不回仓，库存从 locked 核销为 lost，而非归还 available
-- ============================================================================

-- 1. 商品表：新增丢失/货损库存桶
--    库存四桶恒等式：available_stock + locked_stock + sold_stock + lost_stock = 入库总量
ALTER TABLE t_product ADD COLUMN lost_stock INT DEFAULT 0 NOT NULL;
COMMENT ON COLUMN t_product.lost_stock IS '丢失/货损库存：仅退款未收货核销的数量（locked 转入，货物不回仓）';

-- 2. 库存流水表：新增丢失库存变化量（REFUND_LOST 类型流水时为正）
ALTER TABLE t_inventory_transaction ADD COLUMN lost_delta INT DEFAULT 0 NOT NULL;
COMMENT ON COLUMN t_inventory_transaction.lost_delta IS '丢失/货损库存变化量（REFUND_LOST 时为正）';

-- ----------------------------------------------------------------------------
-- 升级后验证（应返回 0 行缺失，且 lost_stock 列存在）：
--   SELECT id, title, available_stock, locked_stock, sold_stock, lost_stock FROM t_product;
-- 回滚（如需）：
--   ALTER TABLE t_product DROP COLUMN lost_stock;
--   ALTER TABLE t_inventory_transaction DROP COLUMN lost_delta;
-- ----------------------------------------------------------------------------
