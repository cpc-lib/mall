package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 本地消息表（事务性发件箱）：
 * 业务事务内落库 PENDING，事务提交后投递 MQ；发送者确认到达后置 SENT；
 * 消费者监听器成功返回（此时 Spring 才向 broker ack）后回写 CONSUMED；
 * 投递重试超限置 FAILED，等待人工补偿。
 */
@Data
@TableName("t_local_message")
public class LocalMessage extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";   // 待投递
    public static final String STATUS_SENT = "SENT";         // 已投递（发送者确认到达）
    public static final String STATUS_CONSUMED = "CONSUMED"; // 已消费（消费者成功处理后回写）
    public static final String STATUS_FAILED = "FAILED";     // 投递重试超限，待人工补偿

    public static final String BIZ_TYPE_ORDER_CLOSE = "ORDER_CLOSE"; // 延迟关单消息
    public static final String BIZ_TYPE_REFUND_SYNC = "REFUND_SYNC"; // 退款状态同步消息

    /** 业务类型：ORDER_CLOSE-延迟关单，REFUND_SYNC-退款状态同步 */
    private String bizType;

    /** 业务单号：orderNo/refundNo */
    private String bizNo;

    /** 消息内容 JSON（与 MQ 消息体一致） */
    private String messageContent;

    /** 状态：PENDING/SENT/CONSUMED/FAILED */
    private String status;

    /** 投递重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private Date nextRetryTime;
}
