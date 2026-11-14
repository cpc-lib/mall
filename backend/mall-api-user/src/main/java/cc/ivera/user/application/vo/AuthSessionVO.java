package cc.ivera.user.application.vo;

import lombok.Data;

/**
 * 管理员会话视图：仅暴露设备信息，不回传 Refresh Token 或摘要。
 */
@Data
public class AuthSessionVO {

    private String sid;

    private String ip;

    private String userAgent;

    private Long issuedAt;

    private Long lastSeenAt;

    /**
     * 是否为当前管理员正在使用的会话。
     */
    private boolean current;
}
