package cc.ivera.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PaymentStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过16个字符")
    private String status;
}
