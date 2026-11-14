package cc.ivera.user.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 用户账号（t_user）：登录凭据 + 角色 + 启停状态。
 */
@Data
public class UserAccount {
    private Long id;
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private String role;
    private String userStatus;
    private Date createTime;
    private Date updateTime;
}
