package cc.ivera.order.domain.enums;

/**
 * 下单 Saga 状态。
 */
public enum SagaStatus {
    PROCESSING("PROCESSING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED");

    private final String type;

    SagaStatus(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
