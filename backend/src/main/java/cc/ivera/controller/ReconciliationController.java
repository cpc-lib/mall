package cc.ivera.controller;

import cc.ivera.dto.bill.ResolveDiscrepancyRequest;
import cc.ivera.entity.bill.BillImport;
import cc.ivera.entity.bill.BillReconcileDiscrepancy;
import cc.ivera.entity.bill.BillRecord;
import cc.ivera.service.bill.BillReconcileService;
import cc.ivera.vo.R;
import cc.ivera.vo.bill.BillDiscrepancyVO;
import cc.ivera.vo.bill.BillImportVO;
import cc.ivera.vo.bill.BillRecordVO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * 微信交易账单上传式对账接口（管理员）。
 * 基路径 /api/reconciliation 由 AuthInterceptor 做登录 + ROLE_ADMIN 鉴权。
 *
 * <p>流程：上传微信交易账单文件（ALL/SUCCESS/REFUND 三种格式均可）→ 流水入库 →
 * 自动对支付记录与退款记录对账 → 差异单查询与人工标记处理。全链路幂等。
 */
@RestController
@RequestMapping("/api/reconciliation")
@CrossOrigin
public class ReconciliationController {

    private final BillReconcileService billReconcileService;

    public ReconciliationController(BillReconcileService billReconcileService) {
        this.billReconcileService = billReconcileService;
    }

    /**
     * 上传微信交易账单文件并自动对账。
     * 同文件重复上传幂等返回原批次；同账单日重复上传被拒绝。
     */
    @PostMapping("/bill/upload")
    public R<BillImportVO> uploadBill(
            @RequestParam("file") MultipartFile file,
            @RequestParam("billDate") String billDate,
            @RequestParam(value = "billType", required = false) String billType) {
        BillImport billImport = billReconcileService.uploadBill(file, billDate, billType);
        return R.ok(BillImportVO.from(billImport));
    }

    /**
     * 对已导入批次重新触发对账；已对账批次幂等返回现有结果。
     */
    @PostMapping("/imports/{importNo}/reconcile")
    public R<BillImportVO> reconcile(@PathVariable String importNo) {
        BillImport billImport = billReconcileService.reconcileByImportNo(importNo);
        return R.ok(BillImportVO.from(billImport));
    }

    /**
     * 导入批次列表（按创建时间倒序），billDate 非空时按账单日期过滤。
     */
    @GetMapping("/imports")
    public R<List<BillImportVO>> listImports(@RequestParam(value = "billDate", required = false) String billDate) {
        return R.ok(BillImportVO.from(billReconcileService.listImports(billDate)));
    }

    /**
     * 导入批次详情。
     */
    @GetMapping("/imports/{importNo}")
    public R<BillImportVO> getImport(@PathVariable String importNo) {
        return R.ok(BillImportVO.from(billReconcileService.getByImportNo(importNo)));
    }

    /**
     * 批次下账单流水，recordType 可选 PAY/REFUND 过滤。
     */
    @GetMapping("/imports/{importNo}/records")
    public R<List<BillRecordVO>> listRecords(
            @PathVariable String importNo,
            @RequestParam(value = "recordType", required = false) String recordType) {
        List<BillRecord> records = billReconcileService.listRecords(importNo, recordType);
        return R.ok(BillRecordVO.from(records));
    }

    /**
     * 批次下对账差异单，bizType/discrepancyType/status 可选过滤。
     */
    @GetMapping("/imports/{importNo}/discrepancies")
    public R<List<BillDiscrepancyVO>> listDiscrepancies(
            @PathVariable String importNo,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "discrepancyType", required = false) String discrepancyType,
            @RequestParam(value = "status", required = false) String status) {
        List<BillReconcileDiscrepancy> discrepancies =
                billReconcileService.listDiscrepancies(importNo, bizType, discrepancyType, status);
        return R.ok(BillDiscrepancyVO.from(discrepancies));
    }

    /**
     * 人工标记差异单已处理。
     */
    @PostMapping("/discrepancies/{id}/resolve")
    public R<BillDiscrepancyVO> resolveDiscrepancy(
            @PathVariable Long id,
            @RequestBody @Valid ResolveDiscrepancyRequest request) {
        BillReconcileDiscrepancy discrepancy =
                billReconcileService.resolveDiscrepancy(id, request.getResolveRemark());
        return R.ok(BillDiscrepancyVO.from(discrepancy));
    }
}
