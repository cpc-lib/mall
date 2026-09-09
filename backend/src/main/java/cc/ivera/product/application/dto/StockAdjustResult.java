package cc.ivera.product.application.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量库存调整（含导入确认入库）的应用层结果：逐条处理，单条失败不影响其他条目。
 * interfaces 层据此组装响应 VO；该类型不直接对外暴露。
 */
@Data
public class StockAdjustResult {

    private int total;

    private int successCount;

    private int failCount;

    private List<Row> rows;

    @Data
    public static class Row {

        private Long productId;

        private Integer delta;

        private Boolean success;

        private Integer stock;

        private String message;
    }
}
