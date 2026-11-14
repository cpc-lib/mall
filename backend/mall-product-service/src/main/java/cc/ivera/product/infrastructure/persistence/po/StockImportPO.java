package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Excel 批量库存导入记录 PO：t_stock_import。
 */
@Data
@TableName("t_stock_import")
public class StockImportPO extends BaseEntity {

    private String fileName;//导入的 Excel 文件名

    private Integer itemCount;//导入明细条数

    private String status;//PENDING-待确认，CONFIRMED-已入库（CAS 抢占防重复执行）

    private String itemsJson;//明细 JSON：[{"productId":13,"delta":50},...]

    private String storageType;//文件存储后端：LOCAL-后端本地磁盘，MINIO-MinIO 对象存储（空按 LOCAL 兼容 V5）

    private String filePath;//文件地址：LOCAL 为文件绝对路径，MINIO 为对象键（stock-import/{id}.xlsx）

    private Date confirmTime;//确认入库时间
}
