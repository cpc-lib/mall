package cc.ivera.service.bill;

import cc.ivera.entity.bill.BillImport;
import cc.ivera.entity.bill.BillReconcileDiscrepancy;
import cc.ivera.entity.bill.BillRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 微信交易账单上传式对账服务。
 *
 * <p>流程：上传账单文件 → 解析支付/退款流水入库 → 自动与本地支付流水(t_payment_info)、
 * 退款单(t_refund_info)对账 → 产出差异单。全链路幂等：同文件重复上传返回原批次、
 * 同账单日重复上传拒绝、重复对账直接返回已有结果、并发对账由分布式锁 + 唯一约束兜底。
 */
public interface BillReconcileService {

    /**
     * 上传微信交易账单：解析入库并自动对账。
     *
     * @param file     账单文件（CSV 文本，ALL/SUCCESS/REFUND 三种微信交易账单均可）
     * @param billDate 账单日期 yyyy-MM-dd（历史日期）
     * @param billType 账单大类，仅支持 tradebill（交易账单），空值按 tradebill 处理
     * @return 导入批次（含对账统计）
     */
    BillImport uploadBill(MultipartFile file, String billDate, String billType);

    /**
     * 对已导入批次重新触发对账；已对账(RECONCILED)批次直接返回现有结果（幂等）。
     */
    BillImport reconcileByImportNo(String importNo);

    /**
     * 按导入批次单号查询批次，不存在抛业务异常。
     */
    BillImport getByImportNo(String importNo);

    /**
     * 查询导入批次列表（按创建时间倒序），billDate 非空时按账单日期过滤。
     */
    List<BillImport> listImports(String billDate);

    /**
     * 查询批次下的账单流水（按 id 升序），recordType 非空时按 PAY/REFUND 过滤。
     */
    List<BillRecord> listRecords(String importNo, String recordType);

    /**
     * 查询批次下的对账差异单（按 id 升序），bizType/discrepancyType/status 非空时过滤。
     */
    List<BillReconcileDiscrepancy> listDiscrepancies(String importNo, String bizType,
                                                     String discrepancyType, String status);

    /**
     * 人工标记差异单已处理（OPEN → RESOLVED），记录处理人与备注。
     */
    BillReconcileDiscrepancy resolveDiscrepancy(Long id, String resolveRemark);
}
