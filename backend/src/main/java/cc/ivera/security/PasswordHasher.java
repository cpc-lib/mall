package cc.ivera.security;

import cc.ivera.exception.BizException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {
    private final SecureRandom secureRandom = new SecureRandom();
    public String newSalt() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    public String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest((salt + ":" + password).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("密码摘要计算失败", e);
        }
    }
    public boolean matches(String password, String salt, String expected) {
        String actual = hash(password, salt);
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
