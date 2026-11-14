package cc.ivera.productservice;

import cc.ivera.shared.infrastructure.config.MyBatisPlusConfig;
import cc.ivera.shared.web.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 商品与库存领域服务入口。
 * 只扫描商品上下文，不装配 BFF 的认证拦截器；共享配置按需显式导入。
 */
@SpringBootApplication(scanBasePackages = "cc.ivera.product")
@Import({MyBatisPlusConfig.class, GlobalExceptionHandler.class})
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
