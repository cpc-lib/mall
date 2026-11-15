package cc.ivera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication(scanBasePackages = "cc.ivera")
@EnableScheduling
@EnableRabbit
@EnableFeignClients(basePackages = "cc.ivera")
public class RefundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RefundServiceApplication.class, args);
    }
}
