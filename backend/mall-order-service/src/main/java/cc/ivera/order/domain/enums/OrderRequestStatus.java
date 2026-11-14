package cc.ivera.order.domain.enums;

/**
 * 下单请求幂等记录状态。
 */
public enum OrderRequestStatus {
    PROCESSING("PROCESSING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String type;

    OrderRequestStatus(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
