package cc.ivera.refund.domain.enums;

/**
 * 已发货退款的商品最终去向。
 */
public enum RefundGoodsDisposition {
    LOST("LOST", "商品丢失/无法回收"),
    RECOVERED("RECOVERED", "商品已全部回收");

    private final String type;
    private final String desc;

    RefundGoodsDisposition(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }
}
