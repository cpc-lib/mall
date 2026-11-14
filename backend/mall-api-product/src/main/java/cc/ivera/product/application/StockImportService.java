package cc.ivera.product.application;

import cc.ivera.product.application.dto.StockAdjustResult;
import cc.ivera.product.domain.model.StockAdjustLine;
import cc.ivera.product.domain.model.StockImport;

import java.util.List;

/**
 * Excel 批量库存导入记录：导入 Excel（文件存后端目录）生成记录（PENDING）
 * → 点选记录，前端 JS（电子表格组件）打开该 Excel 编辑并保存回后端
 * → 确认入库（CAS 抢占防重复，按记录已保存明细逐条执行）。
 */
public interface StockImportService {

    /**
     * 创建导入记录：Excel 文件存到后端目录（{id}.xlsx），明细校验后暂存 PENDING，不改库存，返回记录 id。
     */
    Long createImport(String fileName, String fileBase64, List<StockAdjustLine> items);

    /**
     * 最近导入记录（最多 50 条，含解析后的明细）。
     */
    List<StockImport> listImports();

    /**
     * 按 id 查询导入记录（含解析后的明细），不存在抛 BizException。
     */
    StockImport getImport(Long id);

    /**
     * 读取导入记录对应的 Excel 文件（base64），供前端 JS 打开编辑；不存在抛 BizException。
     */
    String getFile(Long id);

    /**
     * 保存编辑后的 Excel 与明细：仅 PENDING 可修改；覆盖后端文件并同步刷新明细快照与条数。
     */
    void saveImportFile(Long id, String fileBase64, List<StockAdjustLine> items);

    /**
     * 确认入库：CAS 抢占记录（PENDING→CONFIRMED）后按记录已保存明细逐条执行库存调整，返回逐条结果。
     */
    StockAdjustResult confirmImport(Long id);
}
