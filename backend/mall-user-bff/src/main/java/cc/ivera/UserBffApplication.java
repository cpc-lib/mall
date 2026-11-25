package cc.ivera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户端 BFF：只保留用户侧 Controller，领域逻辑全部来自 mall-core。
 * Outbox 中继已按领域拆分到 order/refund/payment 服务，本进程不连接数据库、MQ 与 Redis。
 */
@SpringBootApplication(scanBasePackages = "cc.ivera")
@EnableFeignClients(basePackages = "cc.ivera")
public class UserBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserBffApplication.class, args);
    }

}
