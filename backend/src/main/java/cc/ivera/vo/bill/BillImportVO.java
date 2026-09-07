package cc.ivera.vo.bill;

import cc.ivera.entity.bill.BillImport;
import cc.ivera.enums.bill.BillImportStatus;
import cc.ivera.enums.bill.BillKind;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 账单导入批次视图对象，状态随中文文案透出。
 */
@Data
public class BillImportVO {

    private Long id;

    /**
     * 导入批次业务单号
     */
    private String importNo;

    /**
     * 渠道编码：WXPAY
     */
    private String channelCode;

    /**
     * 账单类型：TRADE-交易账单
     */
    private String billType;

    /**
     * 微信账单种类：ALL/SUCCESS/REFUND
     */
    private String billKind;

    /**
     * 账单种类中文文案
     */
    private String billKindText;

    /**
     * 账单日期 yyyy-MM-dd
     */
    private String billDate;

    /**
     * 上传文件名
     */
    private String fileName;

    private Integer totalRecordCount;

    private Integer payRecordCount;

    private Integer refundRecordCount;

    private Integer badLineCount;

    private Integer matchedCount;

    private Integer discrepancyCount;

    /**
     * 批次状态：IMPORTED/RECONCILED/FAILED
     */
    private String status;

    /**
     * 状态中文文案
     */
    private String statusText;

    private Date reconcileTime;

    private String errorMessage;

    private Date createTime;

    public static BillImportVO from(BillImport entity) {
        if (entity == null) {
            return null;
        }
        BillImportVO vo = new BillImportVO();
        vo.setId(entity.getId());
        vo.setImportNo(entity.getImportNo());
        vo.setChannelCode(entity.getChannelCode());
        vo.setBillType(entity.getBillType());
        vo.setBillKind(entity.getBillKind());
        vo.setBillKindText(BillKind.of(entity.getBillKind()).getDescription());
        vo.setBillDate(entity.getBillDate());
        vo.setFileName(entity.getFileName());
        vo.setTotalRecordCount(entity.getTotalRecordCount());
        vo.setPayRecordCount(entity.getPayRecordCount());
        vo.setRefundRecordCount(entity.getRefundRecordCount());
        vo.setBadLineCount(entity.getBadLineCount());
        vo.setMatchedCount(entity.getMatchedCount());
        vo.setDiscrepancyCount(entity.getDiscrepancyCount());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(resolveStatusText(entity.getStatus()));
        vo.setReconcileTime(entity.getReconcileTime());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    public static List<BillImportVO> from(List<BillImport> entities) {
        List<BillImportVO> result = new ArrayList<>();
        if (entities != null) {
            for (BillImport entity : entities) {
                result.add(from(entity));
            }
        }
        return result;
    }

    private static String resolveStatusText(String status) {
        for (BillImportStatus value : BillImportStatus.values()) {
            if (value.getType().equals(status)) {
                return value.getDescription();
            }
        }
        return status;
    }
}
