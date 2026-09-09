package cc.ivera.product.interfaces.vo;

import lombok.Data;

import java.util.Date;

/**
 * 库存流水行 VO（t_inventory_transaction）。
 */
@Data
public class InventoryTransactionVO {

    private Long id;

    private String bizNo;

    private String operationType;

    private String operationStatus;

    private String orderNo;

    private String refundNo;

    private Long productId;

    private Integer availableDelta;

    private Integer lockedDelta;

    private Integer soldDelta;

    private Integer lostDelta;

    private String errorMessage;

    private Date createTime;
}
