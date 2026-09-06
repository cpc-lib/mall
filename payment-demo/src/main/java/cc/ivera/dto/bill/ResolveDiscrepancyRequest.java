package cc.ivera.dto.bill;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 差异单标记已处理请求。
 */
@Data
public class ResolveDiscrepancyRequest {

    /**
     * 处理备注
     */
    @NotBlank(message = "处理备注不能为空")
    @Size(max = 500, message = "处理备注长度不能超过500个字符")
    private String resolveRemark;
}
