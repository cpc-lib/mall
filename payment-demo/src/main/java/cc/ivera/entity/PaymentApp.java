package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_payment_app")
public class PaymentApp extends BaseEntity {

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
     * 应用配置（JSON格式，包含具体渠道的配置信息）
     */
    private String appConfig;

    /**
     * 排序号
     */
    private Integer sortOrder;
}