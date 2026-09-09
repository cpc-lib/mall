package cc.ivera.shared.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.OptimisticLockerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@MapperScan(basePackages = "cc.ivera", annotationClass = Mapper.class)
@EnableTransactionManagement
public class MyBatisPlusConfig {

    /**
     * 乐观锁插件。
     * <p>
     * 强一致场景并发策略：Redisson 分布式锁按业务键串行 + 状态流转 CAS 条件更新兜底；
     * 订单/支付/退款单状态机一律走「Redis 锁 + CAS 条件更新」双保险，禁止使用 select ... for update
     * （autocommit 下行锁空转、远程调用期间空持锁）。跨锁键同单并发（如跨渠道支付通知 vs 关单、
     * 发货 vs 退款）由同维度 Redis 锁串行化。
     */
    @Bean
    public OptimisticLockerInterceptor optimisticLockerInterceptor() {
        return new OptimisticLockerInterceptor();
    }
}
