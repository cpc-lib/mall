package cc.ivera.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 统一 JVM 默认时区为业务时区 Asia/Shanghai。
 * <p>
 * 订单、支付、退款、物流的时间列由应用时钟写入并与应用时钟比较，达梦会话与账务日切同样按 Asia/Shanghai。
 * 若 JVM 默认时区与数据库会话时区不一致，同一张表会写入两套时间基准（例如物流单 in_transit_time 比
 * shipped_time 快 8 小时），基于时间窗口的状态机扫描会永远命中不到记录。
 * 这里在 Bean 初始化阶段统一默认时区，使 IDE 运行与 java -jar 启动都不依赖宿主机时区。
 */
@Configuration(proxyBeanMethods = false)
public class TimeZoneConfig {

    public static final String ZONE_ID = "Asia/Shanghai";

    static {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE_ID));
    }
}
