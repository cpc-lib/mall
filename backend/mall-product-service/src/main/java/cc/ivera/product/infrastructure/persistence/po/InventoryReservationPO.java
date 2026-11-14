package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 库存预占 PO：t_inventory_reservation。
 */
@Data
@TableName("t_inventory_reservation")
public class InventoryReservationPO extends BaseEntity {

    private String reservationNo;//预占编号

    private String orderNo;//商户订单编号

    private Long orderItemId;//订单明细id（1:1 唯一）

    private Long productId;//商品id（库存单元）

    private Integer quantity;//预占数量

    private String status;//LOCKED/COMMITTED/RELEASED

    private Date expireTime;//预占过期时间（=订单过期时间）

    private Date commitTime;//提交时间（支付成功）

    private Date releaseTime;//释放时间（关单/取消）
}
