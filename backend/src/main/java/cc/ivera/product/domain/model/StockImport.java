package cc.ivera.product.domain.model;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.po.BaseEntity;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * Excel 批量库存导入记录聚合根：每次导入 Excel 生成一条，明细 JSON 存 items_json。
 * 导入仅暂存（PENDING），管理员核对/编辑明细后“确认入库”才逐条执行库存调整；
 * 状态流转：PENDING（待确认）→ CONFIRMED（已入库，CAS 抢占防重复执行）。
 *
 * <p>说明：本聚合承载四桶恒等式中的入库批次审计；逐条调整结果落在
 * InventoryTransaction 流水，本聚合仅追踪批次状态。</p>
 */
@Data
public class StockImport {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private Long id;

    private Date createTime;

    private Date updateTime;

    private String fileName;//导入的 Excel 文件名

    private Integer itemCount;//导入明细条数

    private String status;//PENDING-待确认，CONFIRMED-已入库

    private String itemsJson;//明细 JSON：[{"productId":13,"delta":50},...]

    private String storageType;//文件存储后端：LOCAL / MINIO（空按 LOCAL 兼容 V5）

    private String filePath;//文件地址：LOCAL 为绝对路径，MINIO 为对象键

    private Date confirmTime;//确认入库时间

    /**
     * 明细快照解析结果（非持久化，由 items_json 反序列化填充；PO 无对应列）。
     */
    private List<StockAdjustLine> items;

    /**
     * 创建待确认批次工厂。
     */
    public static StockImport createPending(String fileName, Integer itemCount, String itemsJson) {
        StockImport record = new StockImport();
        record.setFileName(fileName);
        record.setItemCount(itemCount);
        record.setStatus(STATUS_PENDING);
        record.setItemsJson(itemsJson);
        return record;
    }

    /**
     * 编辑前置守卫：仅 PENDING 可修改明细/文件。
     */
    public void requirePendingForEdit() {
        if (!STATUS_PENDING.equals(status)) {
            throw new BizException("该导入记录已确认入库，明细不可再修改");
        }
    }

    /**
     * 确认入库状态推进：仅 PENDING 可执行（并发抢占由仓储 CAS 兜底）。
     */
    public void confirm(Date now) {
        if (!STATUS_PENDING.equals(status)) {
            throw new BizException("该导入记录已确认入库，不能重复执行");
        }
        this.status = STATUS_CONFIRMED;
        this.confirmTime = now;
    }
}
