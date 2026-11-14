package cc.ivera.user.domain.model;

import lombok.Data;

/**
 * 登录会话（Redis 注册表）：一次登录一个 sid，支持按设备查看与精细吊销。
 */
@Data
public class AuthSession {
    private String sid;
    private Long userId;
    private String refreshHash;
    private long issuedAt;
    private long lastSeenAt;
    private String ip;
    private String userAgent;
}
