package cc.ivera.user.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 找回密码申请：用户提交申请，管理员处理时由系统生成一次性随机密码。
 */
@Data
public class PasswordResetRequest {
    private Long id;
    private String username;
    private String remark;
    private String status;
    private String adminRemark;
    private Long handledBy;
    private Date createTime;
    private Date updateTime;
}
