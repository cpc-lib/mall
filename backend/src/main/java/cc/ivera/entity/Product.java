package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_product")
public class Product extends BaseEntity {

    private String title; //商品名称

    private Integer price; //价格（分）

    @TableField("available_stock")
    private Integer stock; //可用库存（DB 列 available_stock；对外 JSON 键名保持 stock 兼容管理端 UI）

    private Integer lockedStock; //锁定库存：下单预占未结转的数量（支付后保持锁定）

    private Integer soldStock; //已售库存：确认收货结转的数量（已售退款回补时扣减）

    private Integer lostStock; //丢失/货损库存：仅退款未收货核销的数量（locked 转入，货物不回仓）

    private String productStatus; //ENABLED / DISABLED
}
