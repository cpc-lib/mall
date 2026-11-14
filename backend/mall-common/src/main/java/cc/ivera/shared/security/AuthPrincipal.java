package cc.ivera.shared.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthPrincipal {
    private Long userId;
    private String username;
    private String role;
    private long tokenVersion;
    /**
     * 会话 ID：双 Token 模型下由 user 域签发；旧 Token 可能为空。
     */
    private String sid;
}
