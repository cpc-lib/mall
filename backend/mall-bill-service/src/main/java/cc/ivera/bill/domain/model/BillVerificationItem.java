package cc.ivera.bill.domain.model;

import lombok.Data;

/**
 * 对账下钻逐字段核验结果。
 */
@Data
public class BillVerificationItem {

    /** 业务单号/渠道流水号/金额/状态 */
    private String field;

    private String channelValue;

    private String localValue;

    /** true=一致，false=不一致，null=当前账本无法直接核验 */
    private Boolean matched;
}
