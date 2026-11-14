package cc.ivera.payment.application.dto;

import lombok.Data;

/**
 * 渠道退款发起结果：退款上下文据此推进本地退款状态。
 */
@Data
public class ChannelRefundResult {

    public enum Outcome {
        SUCCESS,
        PROCESSING,
        FAILED
    }

    private Outcome outcome;

    private String channelRefundId;

    private String rawBody;

    private String errorMessage;

    public static ChannelRefundResult success(String channelRefundId, String rawBody) {
        ChannelRefundResult result = new ChannelRefundResult();
        result.setOutcome(Outcome.SUCCESS);
        result.setChannelRefundId(channelRefundId);
        result.setRawBody(rawBody);
        return result;
    }

    public static ChannelRefundResult processing(String rawBody) {
        ChannelRefundResult result = new ChannelRefundResult();
        result.setOutcome(Outcome.PROCESSING);
        result.setRawBody(rawBody);
        return result;
    }

    public static ChannelRefundResult failed(String rawBody, String errorMessage) {
        ChannelRefundResult result = new ChannelRefundResult();
        result.setOutcome(Outcome.FAILED);
        result.setRawBody(rawBody);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
