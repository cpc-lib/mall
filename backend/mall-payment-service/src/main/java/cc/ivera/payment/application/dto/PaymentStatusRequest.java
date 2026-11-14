package cc.ivera.payment.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class PaymentStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过16个字符")
    private String status;
}
