package cc.ivera.billservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 独立账单服务：只承载微信交易账单导入、账账核对与差异处理。
 * 账单域没有 MQ 消费者与后台扫描，刻意不开启 @EnableScheduling / @EnableRabbit。
 */
@SpringBootApplication(scanBasePackages = "cc.ivera")
@EnableFeignClients(basePackages = "cc.ivera")
public class BillServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillServiceApplication.class, args);
    }

}
