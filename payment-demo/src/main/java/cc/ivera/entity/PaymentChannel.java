package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_payment_channel")
public class PaymentChannel extends BaseEntity {

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 渠道编码
     */
    private String channelCode;

    /**
     * 渠道状态：ENABLED-启用，DISABLED-禁用
     */
    private String channelStatus;

    /**
     * 渠道描述
     */
    private String channelDesc;

    /**
     * 配置参数（JSON格式）
     */
    private String configParams;

    /**
     * 排序号
     */
    private Integer sortOrder;
}