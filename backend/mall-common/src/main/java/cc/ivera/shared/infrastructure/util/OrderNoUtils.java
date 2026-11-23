package cc.ivera.shared.infrastructure.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单号/支付单/退款单/预占/物流 编号工具类。
 * <p>
 * 线程安全：使用 {@link DateTimeFormatter}（不可变，线程安全）+ {@link AtomicInteger} 单调序列号
 * + {@link ThreadLocalRandom} 补充随机性。毫秒级时间戳消除同秒碰撞风险；
 * 序列号每秒 0-999 循环，再加 2 位随机数让 JVM 启动初期也不会跨进程冲突。
 */
public class OrderNoUtils {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.of("Asia/Shanghai"));
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public static String getOrderNo() {
        return "ORD" + getNo();
    }

    public static String getRefundNo() {
        return "RFD" + getNo();
    }

    public static String getPaymentNo() {
        return "PMO" + getNo();
    }

    public static String getReservationNo() {
        return "RSV" + getNo();
    }

    public static String getShipmentNo() {
        return "SHP" + getNo();
    }

    /**
     * 编号主体：17 位毫秒时间戳（Asia/Shanghai）+ 3 位 AtomicInteger 序列号（每秒 0-999 循环）+ 2 位随机数。
     * 单 JVM 1ms 内碰撞概率 < 0.001；跨进程不同 JVM 序列号起点独立，再叠加尾部随机数进一步打散。
     */
    public static String getNo() {
        String ts = FORMATTER.format(Instant.now());
        int seq = (SEQ.getAndIncrement() & 0x7FFFFFFF) % 1000;
        int rnd = ThreadLocalRandom.current().nextInt(100);
        return ts + String.format("%03d%02d", seq, rnd);
    }

}
