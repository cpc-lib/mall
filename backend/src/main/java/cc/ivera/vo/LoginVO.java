package cc.ivera.vo;

import cc.ivera.entity.UserAccount;
import lombok.Data;

/**
 * 登录/刷新令牌对响应 VO。
 */
@Data
public class LoginVO {

    private String accessToken;

    private Long accessExpiresIn;

    private String refreshToken;

    private Long refreshExpiresIn;

    private UserAccount user;
}
