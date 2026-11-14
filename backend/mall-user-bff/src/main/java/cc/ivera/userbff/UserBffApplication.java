package cc.ivera.userbff;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户端 BFF：只保留用户侧 Controller，领域逻辑全部来自 mall-core。
 * 后台任务（@Scheduled / @RabbitListener）只在本进程启用，避免与 admin-bff 重复消费。
 */
@SpringBootApplication(scanBasePackages = "cc.ivera")
@EnableRabbit
@EnableScheduling
@EnableFeignClients(basePackages = "cc.ivera")
public class UserBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserBffApplication.class, args);
    }

}
