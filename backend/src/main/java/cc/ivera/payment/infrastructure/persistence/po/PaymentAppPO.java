package cc.ivera.payment.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 支付应用 PO：t_payment_app。
 */
@Data
@TableName("t_payment_app")
public class PaymentAppPO extends BaseEntity {

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用编码
     */
    private String appCode;

    /**
     * 应用状态：ENABLED-启用，DISABLED-禁用
     */
    private String appStatus;

    /**
     * 关联渠道ID
     */
    private Long channelId;

    /**
     * 应用描述
     */
    private String appDesc;

    /**
     * 排序号
     */
    private Integer sortOrder;
}
