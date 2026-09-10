package cc.ivera.bill.application.parser;

import cc.ivera.bill.domain.model.BillRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 微信交易账单解析结果（生产来源为 XLSX）。
 * payRecords/refundRecords 为解析出的有效流水（importId 由入库时回填）；
 * totalLines 为扫描到的物理行数；badLines 为形似数据行但无法解析、被跳过的坏行数。
 * billKind 为按表头识别出的微信账单种类：ALL/SUCCESS/REFUND。
 */
@Data
public class ParsedBill {

    /**
     * 支付流水（交易状态为 SUCCESS 的账单行）
     */
    private List<BillRecord> payRecords = new ArrayList<>();

    /**
     * 退款流水（交易状态为 REFUND/REVOKED 的账单行）
     */
    private List<BillRecord> refundRecords = new ArrayList<>();

    /**
     * 扫描到的物理行数（含表头/汇总/空行/坏行）
     */
    private int totalLines;

    /**
     * 坏行数（首字段为时间但关键字段缺失、金额或时间不可解析而被跳过的数据行）
     */
    private int badLines;

    /**
     * 微信账单种类：ALL（全部）、SUCCESS（支付账单）、REFUND（退款账单）
     */
    private String billKind;

    /**
     * 有效记录总数 = 支付记录数 + 退款记录数
     */
    public int validRecordCount() {
        return payRecords.size() + refundRecords.size();
    }
}
