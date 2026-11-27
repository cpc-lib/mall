package cc.ivera.order.domain.repository;

import java.util.Date;
import java.util.List;

/**
 * Seata Saga 历史实例清理仓储端口。
 * <p>仅清理超过保留期的成功终态根实例（status=SU、compensation_status 为空、未运行、无子实例），
 * 不触碰 RU/UN/FA/SK、补偿中、已补偿或仍在运行的记录，也不删除状态机定义表。</p>
 */
public interface SagaHistoryRepository {

    /**
     * 查询可清理的成功根实例 ID。
     */
    List<String> findCleanableInstanceIds(Date cutoff, int limit);

    /**
     * 按实例 ID 批量删除状态明细（先于实例删除，避免出现无主明细）。
     */
    int deleteStateInstancesByMachineInstIds(List<String> ids);

    /**
     * 按终态条件批量删除实例（CAS 守卫：仅删除仍满足成功终态的行）。
     * 返回实际删除行数，调用方需校验与选中数一致，不一致说明查询后状态被推进，应回滚。
     */
    int deleteMachineInstancesTerminalBefore(Date cutoff, List<String> ids);
}
