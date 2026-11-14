package cc.ivera.payment.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class PaymentAppRequest {

    @NotBlank(message = "应用名称不能为空")
    @Size(max = 64, message = "应用名称长度不能超过64个字符")
    private String appName;

    @NotBlank(message = "应用编码不能为空")
    @Size(max = 64, message = "应用编码长度不能超过64个字符")
    private String appCode;

    @Size(max = 16, message = "应用状态长度不能超过16个字符")
    private String appStatus;

    @NotNull(message = "支付渠道不能为空")
    private Long channelId;

    @Size(max = 255, message = "应用描述长度不能超过255个字符")
    private String appDesc;

    private Integer sortOrder;
}
