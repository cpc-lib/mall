package cc.ivera.payment.application.dto;

import lombok.Data;

/**
 * 支付服务内部接口：按渠道 + 支付时间区间查询成功支付单。
 * 时间使用 epoch millis 传输，避免跨进程日期格式差异。
 */
@Data
public class InternalPaidTimeRangeRequest {

    private String channel;

    private long startInclusiveMillis;

    private long endExclusiveMillis;
}
