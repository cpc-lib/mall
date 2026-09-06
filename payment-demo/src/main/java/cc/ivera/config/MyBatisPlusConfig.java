package cc.ivera.config;

import com.baomidou.mybatisplus.extension.plugins.OptimisticLockerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@MapperScan("cc.ivera.mapper")
@EnableTransactionManagement
public class MyBatisPlusConfig {

    /**
     * 乐观锁插件。
     *
     * 当前订单创建、退款申请等强一致场景主要使用 select ... for update 悲观锁；
     * 这里同时开启乐观锁能力，便于后续对版本号字段进行并发更新保护。
     */
    @Bean
    public OptimisticLockerInterceptor optimisticLockerInterceptor() {
        return new OptimisticLockerInterceptor();
    }
}
