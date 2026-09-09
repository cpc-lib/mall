package cc.ivera.user.interfaces.vo;

import lombok.Data;

/**
 * 管理员处理密码重置申请响应 VO。
 */
@Data
public class PasswordResetHandleVO {

    private Long id;

    private String username;

    private String newPassword;
}
