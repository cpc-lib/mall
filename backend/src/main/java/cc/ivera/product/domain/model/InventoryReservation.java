package cc.ivera.product.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 库存预占（记录型）：每条订单明细对应一条预占（1:1，order_item_id 唯一）。
 * 状态机：LOCKED → COMMITTED（支付成功）/ RELEASED（关单/取消）；
 * 状态迁移由仓储 CAS 条件 UPDATE 完成（整单维度）。
 */
@Data
public class InventoryReservation {

    private Long id;

    private Date createTime;

    private Date updateTime;

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
