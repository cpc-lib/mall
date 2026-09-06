package cc.ivera.vo;

import lombok.Data;

import java.util.Date;

/**
 * 支付应用视图 VO（含关联渠道信息）。
 */
@Data
public class PaymentAppViewVO {

    private Long id;

    private String appName;

    private String appCode;

    private String appStatus;

    private Long channelId;

    private String channelCode;

    private String channelName;

    private String appDesc;

    /** 仅管理端列表返回，前端支付页不返回 */
    private String appConfig;

    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;
}
