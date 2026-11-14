package cc.ivera.bill.application;

import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.model.BillReconcileDrilldown;
import cc.ivera.bill.domain.model.BillReconcileSummary;
import cc.ivera.bill.domain.model.BillRecord;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 微信交易账单账账核对服务。
 *
 * <p>流程：上传渠道账单 → 归一化为渠道账(t_bill_record) → 与平台交易账
 * (t_payment_order + t_refund_order)做双向账账核对 → 产出汇总平衡结果与差异单。全链路幂等：同文件重复上传返回原批次、
 * 同账单日重复上传拒绝、重复对账直接返回已有结果、并发对账由分布式锁 + 唯一约束兜底。
 */
public interface BillReconcileService {

    /**
     * 上传微信交易账单：解析入库并自动对账。
     *
     * @param file     微信交易账单 XLSX 文件（.xlsx，ALL/SUCCESS/REFUND 三种账单均可）
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
     * 账账核对汇总：渠道/平台支付与退款笔数、金额、净额以及是否平账。
     */
    BillReconcileSummary getSummary(String importNo);

    /**
     * 单条差异数据下钻：同时返回渠道原始账、平台账本候选记录与逐字段核验结果。
     */
    BillReconcileDrilldown getDrilldown(Long discrepancyId);

    /**
     * 人工标记差异单已处理（OPEN → RESOLVED），记录处理人与备注。
     */
    BillReconcileDiscrepancy resolveDiscrepancy(Long id, String resolveRemark);
}
