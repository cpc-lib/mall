package cc.ivera.product.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存预占状态（落 t_inventory_reservation.status）。
 * 状态机：LOCKED → COMMITTED（支付成功）/ RELEASED（关单/取消）。
 */
@AllArgsConstructor
@Getter
public enum ReservationStatus {

    /**
     * 锁定（下单预占）
     */
    LOCKED("LOCKED"),

    /**
     * 已提交（支付成功，锁定转实扣）
     */
    COMMITTED("COMMITTED"),

    /**
     * 已释放（关单/取消，归还可用库存）
     */
    RELEASED("RELEASED");

    /**
     * 类型
     */
    private final String type;
}
