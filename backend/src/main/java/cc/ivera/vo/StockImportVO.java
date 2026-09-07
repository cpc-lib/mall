package cc.ivera.vo;

import cc.ivera.dto.admin.ProductStockBatchAdjustRequest;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Excel 库存导入记录 VO。
 */
@Data
public class StockImportVO {

    private Long id;

    private String fileName;

    private Integer itemCount;

    private String status;

    private List<ProductStockBatchAdjustRequest.Item> items;

    private Date createTime;

    private Date confirmTime;
}
