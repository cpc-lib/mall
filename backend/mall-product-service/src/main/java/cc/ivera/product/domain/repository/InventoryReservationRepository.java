package cc.ivera.product.domain.repository;

import cc.ivera.product.domain.model.InventoryReservation;

import java.util.List;

/**
 * 库存预占记录型仓储端口：追加预占、整单维度 CAS 状态迁移、按单查询。
 */
public interface InventoryReservationRepository {

    void insert(InventoryReservation reservation);

    /**
     * 预占状态 CAS 迁移（整单维度）。
     * timeColumn：commitTime → 写 commit_time；releaseTime → 写 release_time。
     */
    int casTransition(String orderNo, String from, String to, String timeColumn);

    List<InventoryReservation> listByOrderNo(String orderNo);
}
