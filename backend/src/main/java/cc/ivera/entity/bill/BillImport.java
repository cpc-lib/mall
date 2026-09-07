package cc.ivera.entity.bill;

import cc.ivera.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 账单导入批次表：管理员上传渠道账单文件的导入与对账批次。
 * 幂等约束：import_no 唯一；(channel_code, bill_type, bill_date, bill_kind) 唯一；file_hash 唯一。
 */
@Data
@TableName("t_bill_import")
public class BillImport extends BaseEntity {

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
     * 微信交易账单种类：ALL-全部账单，SUCCESS-支付成功账单，REFUND-退款账单
     */
    private String billKind;

    /**
     * 账单日期，格式 yyyy-MM-dd
     */
    private String billDate;

    /**
     * 上传的账单文件名
     */
    private String fileName;

    /**
     * 账单文件内容 SHA-256
     */
    private String fileHash;

    /**
     * 账单有效记录总笔数
     */
    private Integer totalRecordCount;

    /**
     * 账单支付记录笔数
     */
    private Integer payRecordCount;

    /**
     * 账单退款记录笔数
     */
    private Integer refundRecordCount;

    /**
     * 解析失败被跳过的坏行数
     */
    private Integer badLineCount;

    /**
     * 对账匹配成功笔数
     */
    private Integer matchedCount;

    /**
     * 对账差异笔数
     */
    private Integer discrepancyCount;

    /**
     * 批次状态：IMPORTED-已导入，RECONCILED-已对账，FAILED-失败
     */
    private String status;

    /**
     * 对账完成时间
     */
    private Date reconcileTime;

    /**
     * 失败原因
     */
    private String errorMessage;
}
