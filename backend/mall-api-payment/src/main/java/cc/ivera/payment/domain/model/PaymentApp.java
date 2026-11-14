package cc.ivera.payment.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 支付应用（t_payment_app）：应用业务信息（名称/编码/状态/描述/排序/所属渠道），不保存商户密钥。
 */
@Data
public class PaymentApp {

    private Long id;

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

    private Date createTime;

    private Date updateTime;
}
