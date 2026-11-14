package cc.ivera.user.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_password_reset_request")
public class PasswordResetRequestPO extends BaseEntity {
    private String username;
    private String remark;
    private String status;
    private String adminRemark;
    private Long handledBy;
}
