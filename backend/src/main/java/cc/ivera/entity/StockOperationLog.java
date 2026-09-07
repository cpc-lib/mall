package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_stock_operation_log")
public class StockOperationLog extends BaseEntity {
    private String bizNo;
    private String orderNo;
    private String refundNo;
    private String operationType;
    private String operationStatus;
    private Integer retryCount;
    private String payload;
    private String errorMessage;
}
