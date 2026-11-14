package cc.ivera.refund.application.dto;

import lombok.Data;

import jakarta.validation.constraints.Size;

@Data
public class RefundApproveRequest {

    /**
     * 审核备注
     */
    @Size(max = 255, message = "审核备注长度不能超过255个字符")
    private String approveRemark;
}
