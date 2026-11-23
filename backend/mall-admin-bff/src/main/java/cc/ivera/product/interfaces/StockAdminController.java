package cc.ivera.product.interfaces;


import cc.ivera.product.application.ProductStockService;
import cc.ivera.product.application.StockImportService;
import cc.ivera.product.domain.model.StockAdjustLine;
import cc.ivera.product.domain.model.StockImport;
import cc.ivera.product.interfaces.assembler.ProductViewAssembler;
import cc.ivera.product.application.dto.StockImportCreateRequest;
import cc.ivera.product.application.dto.StockImportFileSaveRequest;
import cc.ivera.product.application.vo.BatchAdjustResultVO;
import cc.ivera.product.application.vo.InventoryTransactionVO;
import cc.ivera.product.application.vo.StockImportVO;
import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 库存流水分页查询走 V2 流水表 t_inventory_transaction。
 * V5 起：Excel 批量库存导入记录（文件存后端目录：创建 / 列表 / 读取文件 / 保存编辑 / 确认入库）。
 */
@RestController
@RequestMapping("/api/admin/stock")
@CrossOrigin
public class StockAdminController {
    private final ProductStockService productStockService;
    private final StockImportService stockImportService;

    public StockAdminController(ProductStockService productStockService, StockImportService stockImportService) {
        this.productStockService = productStockService;
        this.stockImportService = stockImportService;
    }

    @Operation(summary = "库存流水分页查询（可按商品/类型/状态过滤）")
    @GetMapping("/transactions")
    public R<PageVO<InventoryTransactionVO>> transactions(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) Long productId,
                                                          @RequestParam(required = false) String bizType,
                                                          @RequestParam(required = false) String status) {
        return R.ok(ProductViewAssembler.toTransactionPage(
            productStockService.pageTransactions(page, size, productId, bizType, status)));
    }

    @Operation(summary = "创建 Excel 导入记录：文件存后端目录，明细暂存 PENDING（不改库存）")
    @PostMapping("/imports")
    public R<StockImportVO> createImport(@Valid @RequestBody StockImportCreateRequest req) {
        List<StockAdjustLine> items = ProductViewAssembler.toAdjustLines(req.getItems());
        Long id = stockImportService.createImport(req.getFileName(), req.getFileBase64(), items);
        return R.ok(ProductViewAssembler.toStockImportVO(stockImportService.getImport(id)))
            .setMessage("导入记录已创建，请点选该记录进行编辑或确认入库");
    }

    @Operation(summary = "Excel 导入记录分页（含明细）")
    @GetMapping("/imports")
    public R<PageVO<StockImportVO>> imports(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        PageVO<StockImport> result = stockImportService.listImports(page, size);
        PageVO<StockImportVO> vo = new PageVO<>();
        vo.setTotal(result.getTotal());
        vo.setPage(result.getPage());
        vo.setSize(result.getSize());
        vo.setRecords(ProductViewAssembler.toStockImportVOList(result.getRecords()));
        return R.ok(vo);
    }

    @Operation(summary = "按 id 查询导入记录（含明细）")
    @GetMapping("/imports/{id}")
    public R<StockImportVO> getImport(@PathVariable Long id) {
        return R.ok(ProductViewAssembler.toStockImportVO(stockImportService.getImport(id)));
    }

    @Operation(summary = "读取导入记录的 Excel 文件（base64），供前端 JS 打开编辑")
    @GetMapping("/imports/{id}/file")
    public R<String> importFile(@PathVariable Long id) {
        return R.ok(stockImportService.getFile(id));
    }

    @Operation(summary = "保存编辑后的 Excel 与明细（仅 PENDING；覆盖后端文件并刷新明细快照）")
    @PutMapping("/imports/{id}/file")
    public R<StockImportVO> saveImportFile(@PathVariable Long id, @Valid @RequestBody StockImportFileSaveRequest req) {
        stockImportService.saveImportFile(id, req.getFileBase64(), ProductViewAssembler.toAdjustLines(req.getItems()));
        return R.ok(ProductViewAssembler.toStockImportVO(stockImportService.getImport(id)))
            .setMessage("导入明细已保存，可继续编辑或确认入库");
    }

    @Operation(summary = "确认入库：CAS 抢占记录后按已保存明细逐条执行库存调整")
    @PostMapping("/imports/{id}/confirm")
    public R<BatchAdjustResultVO> confirmImport(@PathVariable Long id) {
        BatchAdjustResultVO body = ProductViewAssembler.toBatchAdjustResultVO(stockImportService.confirmImport(id));
        return R.ok(body).setMessage("确认入库完成：成功 " + body.getSuccessCount() + " 条，失败 " + body.getFailCount() + " 条");
    }
}
