package cc.ivera.refund.domain.repository;

import cc.ivera.refund.domain.model.RefundInfo;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 渠道退款流水仓储端口（t_refund_info）。
 * 退款状态推进一律走端口内 CAS（refund_no + 当前状态条件 UPDATE），禁止先改后查。
 */
public interface RefundInfoRepository {

    /**
     * 新建渠道退款流水（id 回填）；重复退款单号由唯一键抛 DuplicateKeyException，调用方幂等处理。
     */
    void save(RefundInfo refundInfo);

    /**
     * 按退款单号查询（不加锁）。
     * 退款并发安全由 {@link #updateStatusIfCurrentIn} / casApproval* 等 CAS 条件更新保证。
     */
    RefundInfo findByRefundNo(String refundNo);

    /**
     * 订单全部渠道退款流水按创建时间倒序。
     */
    List<RefundInfo> listByOrderNoCreateTimeDesc(String orderNo);

    /**
     * 全量渠道退款流水按创建时间倒序。
     */
    List<RefundInfo> listAllCreateTimeDesc();

    /**
     * 退款状态 CAS：refund_no + refund_status IN(currentStatusTypes) 命中时，
     * 落 targetStatusType，并按非空覆盖 refundId/contentReturn/contentNotify。
     *
     * @return true=CAS 成功；false=当前状态不匹配（并发已处理，幂等）
     */
    boolean updateStatusIfCurrentIn(String refundNo,
                                    String refundId,
                                    String targetStatusType,
                                    String contentReturn,
                                    String contentNotify,
                                    Collection<String> currentStatusTypes);

    /**
     * 审核通过 CAS：approval_status=PENDING 且 refund_status IN(CREATED/FAILED/ABNORMAL)，
     * 置 approval_status=APPROVED + 审核备注/时间。
     */
    boolean casApprovalPassed(String refundNo, String approveRemark, Date approvedTime);

    /**
     * 审核拒绝 CAS：approval_status=PENDING，置 approval_status=REJECTED + 备注/时间 + refund_status=CLOSED。
     */
    boolean casApprovalRejected(String refundNo, String approveRemark, Date approvedTime);

    /**
     * 按退款单号更新非空补丁字段（渠道对账补录修正 orderNo/refundId/totalFee/refund 等）；refundNo 仅作条件。
     */
    void updateByRefundNo(RefundInfo patch);

    /**
     * 汇总订单在指定退款状态集合下的退款金额（分），无记录返回 0。
     */
    Integer sumRefundAmountByOrderNoAndStatuses(String orderNo, Collection<String> statusTypes);

    /**
     * 兜底扫描：已审核通过（APPROVED）且退款处理中（PROCESSING），按创建时间升序；
     * 限量由仓储实现按数据库方言处理（DM8 LIMIT 降级）。
     */
    List<RefundInfo> listProcessingApproved();

    /**
     * 渠道对账：按商户退款单号集合查退款流水（账单正向核对）。
     */
    List<RefundInfo> listByRefundNos(Collection<String> refundNos);

    /**
     * 渠道对账反向扫描：指定退款状态 + 创建时间区间 [start, end) 的退款流水。
     */
    List<RefundInfo> listByStatusAndCreateTimeRange(String refundStatus, Date start, Date end);
}
