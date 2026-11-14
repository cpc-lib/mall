package cc.ivera.adminbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理端 BFF：只保留管理侧 Controller，领域逻辑全部来自 mall-core。
 * 刻意不开启 @EnableRabbit / @EnableScheduling：MQ 消费者与定时兜底统一由 user-bff 进程执行，
 * 本进程只提供管理端入口。
 */
@SpringBootApplication(scanBasePackages = "cc.ivera")
@EnableFeignClients(basePackages = "cc.ivera")
public class AdminBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminBffApplication.class, args);
    }

}
