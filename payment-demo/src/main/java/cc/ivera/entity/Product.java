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

    private Integer lockedStock; //锁定库存：下单预占未提交/未释放的数量

    private String productStatus; //ENABLED / DISABLED
}
