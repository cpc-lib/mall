package cc.ivera.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PaymentChannelRequest {

    @NotBlank(message = "渠道名称不能为空")
    @Size(max = 64, message = "渠道名称长度不能超过64个字符")
    private String channelName;

    @NotBlank(message = "渠道编码不能为空")
    @Size(max = 32, message = "渠道编码长度不能超过32个字符")
    private String channelCode;

    @Size(max = 16, message = "渠道状态长度不能超过16个字符")
    private String channelStatus;

    @Size(max = 255, message = "渠道描述长度不能超过255个字符")
    private String channelDesc;

    /**
     * 渠道公共参数，JSON 对象字符串，例如：{"domain":"https://api.mch.weixin.qq.com"}
     */
    private String configParams;

    private Integer sortOrder;
}
