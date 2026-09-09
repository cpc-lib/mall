package cc.ivera.product.interfaces.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量库存调整结果 VO。
 */
@Data
public class BatchAdjustResultVO {

    private Integer total;

    private Integer successCount;

    private Integer failCount;

    private List<BatchAdjustItemVO> results;
}
