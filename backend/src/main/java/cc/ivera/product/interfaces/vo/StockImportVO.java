package cc.ivera.product.interfaces.vo;

import cc.ivera.product.domain.model.StockAdjustLine;
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

    private List<StockAdjustLine> items;

    private Date createTime;

    private Date confirmTime;
}
