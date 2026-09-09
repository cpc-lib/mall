package cc.ivera.user.interfaces.vo;

import lombok.Data;

import java.util.Date;

/**
 * 管理员用户列表/详情行 VO。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String role;

    private String userStatus;

    private Date createTime;

    private Date updateTime;

    /**
     * 仅详情接口返回：是否在线
     */
    private Boolean online;
}
