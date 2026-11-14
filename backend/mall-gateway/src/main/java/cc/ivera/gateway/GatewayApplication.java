package cc.ivera.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 商城统一网关：占用 8080，两个前端无需改动 baseURL。
 * 当前阶段只做路由与 CORS；Access Token 验签仍由核心应用权威校验，
 * 待 BFF / 领域服务独立部署后再下沉到网关。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
