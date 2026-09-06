package cc.ivera.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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

    /**
     * 应用级参数，JSON 对象字符串。字段由具体渠道决定，不做 mock 默认值。
     */
    private String appConfig;

    private Integer sortOrder;
}
