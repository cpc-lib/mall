package cc.ivera.security;

import cc.ivera.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtTokenService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long accessTtlSeconds;

    public JwtTokenService(ObjectMapper objectMapper,
                           @Value("${payment.auth.jwt-secret:${AUTH_JWT_SECRET:payment-demo-dev-secret-change-me-2026}}") String secret,
                           @Value("${payment.auth.access-ttl-seconds:1800}") long accessTtlSeconds) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String issue(Long userId, String username, String role, long tokenVersion) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(java.util.Collections.singletonMap("alg", "HS256")));
            Map<String,Object> payload = new LinkedHashMap<>();
            long now = System.currentTimeMillis() / 1000L;
            payload.put("uid", userId);
            payload.put("sub", username);
            payload.put("role", role);
            payload.put("ver", tokenVersion);
            payload.put("iat", now);
            payload.put("exp", now + accessTtlSeconds);
            String body = encode(objectMapper.writeValueAsBytes(payload));
            String content = header + "." + body;
            return content + "." + sign(content);
        } catch (Exception e) {
            throw new BizException("Access Token 生成失败", e);
        }
    }

    public AuthPrincipal parse(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) throw new BizException("Access Token 格式非法");
            String content = parts[0] + "." + parts[1];
            if (!constantEquals(sign(content), parts[2])) throw new BizException("Access Token 签名非法");
            Map<String,Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<Map<String,Object>>(){});
            long exp = ((Number) payload.get("exp")).longValue();
            if (System.currentTimeMillis() / 1000L >= exp) throw new BizException("Access Token 已过期");
            return new AuthPrincipal(((Number) payload.get("uid")).longValue(), String.valueOf(payload.get("sub")), String.valueOf(payload.get("role")), ((Number) payload.get("ver")).longValue());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Access Token 解析失败", e);
        }
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return encode(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }
    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private boolean constantEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
