package cc.ivera.product.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;

/**
 * 库存调整失败流水记录请求：调用方在调整被拒后单独登记 FAILED 流水。
 */
@Data
public class StockAdjustFailedRequest {

    @NotNull
    private Integer delta;

    private String reason;
}
