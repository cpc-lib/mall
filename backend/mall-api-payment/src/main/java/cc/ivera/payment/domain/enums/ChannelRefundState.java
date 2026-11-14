package cc.ivera.payment.domain.enums;

/**
 * 渠道退款状态归一：各支付渠道状态在支付上下文内统一映射后回传退款上下文。
 */
public enum ChannelRefundState {

    SUCCESS,

    PROCESSING,

    ABNORMAL,

    CLOSED,

    UNKNOWN
}
