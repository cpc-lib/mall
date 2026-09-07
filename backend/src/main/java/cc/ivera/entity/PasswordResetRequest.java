package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_password_reset_request")
public class PasswordResetRequest extends BaseEntity {
    private String username;
    private String remark;
    private String status;
    private String adminRemark;
    private Long handledBy;
}
