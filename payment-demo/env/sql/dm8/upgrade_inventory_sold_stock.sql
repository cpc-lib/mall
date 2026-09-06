-- =====================================================================
-- 库存三桶模型升级脚本（V7 → 可用/锁定/已售 + 确认收货结转）
-- 适用：已按旧版 payment_demo.sql 初始化的存量 DM8 库。
-- 新库直接执行 payment_demo.sql，无需本脚本。
-- 内容：
--   1. t_product 新增 sold_stock（已售库存，确认收货结转）
--   2. t_inventory_transaction 新增 sold_delta（已售库存变化量）
--   3. 存量数据一次性账实回填：
--      - 已收货订单明细结转 sold_stock
--      - locked_stock = 未支付预占(LOCKED) + 在途已支付订单明细
--        （旧模型支付成功时即扣锁定，按新模型需保持锁定至确认收货）
-- 幂等性：DM8 不支持 IF EXISTS 增列，重复执行会在增列处报错，属预期。
-- =====================================================================

-- 1. t_product 新增已售库存列
ALTER TABLE t_product ADD sold_stock INT DEFAULT 0 NOT NULL;
COMMENT ON COLUMN t_product.sold_stock IS '已售库存：确认收货结转的数量（已售退款回补时扣减）';

-- 2. t_inventory_transaction 新增已售变化量列
ALTER TABLE t_inventory_transaction ADD sold_delta INT DEFAULT 0 NOT NULL;
COMMENT ON COLUMN t_inventory_transaction.sold_delta IS '已售库存变化量';

-- 3. 存量回填：已收货订单 → sold_stock（退款回补过的数量已含在 restocked_qty 扣减中）
UPDATE t_product
SET sold_stock = NVL((SELECT SUM(oi.quantity - NVL(oi.restocked_qty, 0))
                        FROM t_order_item oi
                        JOIN t_order_info o ON o.order_no = oi.order_no
                       WHERE oi.product_id = t_product.id
                         AND o.fulfillment_status = 'RECEIVED'), 0);

-- 4. 存量回填：locked_stock = 未支付预占 + 在途已支付（旧模型支付即扣锁定，此处补回）
UPDATE t_product
SET locked_stock = NVL((SELECT SUM(r.quantity)
                          FROM t_inventory_reservation r
                         WHERE r.product_id = t_product.id
                           AND r.status = 'LOCKED'), 0)
                + NVL((SELECT SUM(oi.quantity - NVL(oi.restocked_qty, 0))
                         FROM t_order_item oi
                         JOIN t_order_info o ON o.order_no = oi.order_no
                        WHERE oi.product_id = t_product.id
                          AND o.pay_status = 'PAID'
                          AND o.fulfillment_status IN ('WAIT_SHIP', 'SHIPPED')), 0);

COMMIT;
