package cc.ivera.shared.infrastructure.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生产环境凭据守卫。
 * <p>
 * 仓库配置中 DM / RabbitMQ / Redis 口令与 JWT 密钥都带同名环境变量占位符，缺省值为开发库凭据。
 * 若生产环境漏配环境变量，进程会用仓库默认凭据静默启动。这里在 profiles 含 {@code prod} 时启动即校验：
 * 已配置但为空、或仍等于仓库默认值的凭据直接导致启动失败。
 * <p>
 * 未配置（解析结果为 null）的键视为该进程不使用该凭据（例如商品服务不接 RabbitMQ），跳过校验。
 * 异常信息只输出配置键名，不输出任何口令明文。
 */
@Component
@Profile("prod")
public class ProductionSecretGuard implements InitializingBean {

    private static final Map<String, String> BUILT_IN_DEFAULTS = new LinkedHashMap<>();

    static {
        BUILT_IN_DEFAULTS.put("spring.datasource.password", "Cpc2026#@Dm");
        BUILT_IN_DEFAULTS.put("spring.rabbitmq.password", "Cpc2026#@Mq");
        BUILT_IN_DEFAULTS.put("spring.data.redis.password", "cpc!23#@");
        BUILT_IN_DEFAULTS.put("payment.auth.jwt-secret", "1SloQ0WmJUmNWiqvNOPco016eVlkYAskLrJE2FJ");
        BUILT_IN_DEFAULTS.put("stock.import.minio.secret-key", "hjm029796");
        BUILT_IN_DEFAULTS.put("spring.cloud.nacos.password", "nacos");
    }

    private final Environment environment;

    public ProductionSecretGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        StringBuilder problems = new StringBuilder();
        BUILT_IN_DEFAULTS.forEach((key, builtIn) -> {
            String value = environment.getProperty(key);
            if (value == null) {
                return;
            }
            if (value.isBlank()) {
                problems.append(System.lineSeparator()).append("  - ").append(key).append(" 为空");
            } else if (value.strip().equals(builtIn)) {
                problems.append(System.lineSeparator()).append("  - ").append(key).append(" 仍使用仓库默认值");
            }
        });
        if (problems.length() > 0) {
            throw new IllegalStateException(
                    "生产环境(prod)凭据校验失败，请通过环境变量注入真实凭据:" + problems);
        }
    }
}
