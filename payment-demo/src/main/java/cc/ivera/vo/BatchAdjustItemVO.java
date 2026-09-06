package cc.ivera.vo;

import lombok.Data;

/**
 * 批量库存调整单条结果 VO。
 */
@Data
public class BatchAdjustItemVO {

    private Long productId;

    private Integer delta;

    private Boolean success;

    private Integer stock;

    private String message;
}
