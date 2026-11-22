package cc.ivera.order.infrastructure.persistence.mapper;

import cc.ivera.order.infrastructure.persistence.po.OrderInfoPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfoPO> {

    /**
     * 用户订单分页：按创建时间倒序，LIMIT/OFFSET 由服务端换算。
     */
    List<OrderInfoPO> selectUserOrderPage(@Param("userId") Long userId,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);

    /**
     * 用户订单总数。
     */
    Long countUserOrders(@Param("userId") Long userId);

    /**
     * 管理员全部订单动态条件查询（XML 动态 SQL）：支付/生命周期/履约状态等值、订单号模糊、
     * 用户编号精确、创建时间闭区间，全部可选；按创建时间倒序，LIMIT/OFFSET 分页。
     */
    List<OrderInfoPO> selectAdminOrderList(@Param("payStatus") String payStatus,
                                           @Param("orderStatus") String orderStatus,
                                           @Param("fulfillmentStatus") String fulfillmentStatus,
                                           @Param("orderNoLike") String orderNoLike,
                                           @Param("userId") Long userId,
                                           @Param("startTime") Date startTime,
                                           @Param("endTime") Date endTime,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

    /**
     * 管理员订单总数：筛选条件与 {@link #selectAdminOrderList} 完全一致。
     */
    Long countAdminOrderList(@Param("payStatus") String payStatus,
                             @Param("orderStatus") String orderStatus,
                             @Param("fulfillmentStatus") String fulfillmentStatus,
                             @Param("orderNoLike") String orderNoLike,
                             @Param("userId") Long userId,
                             @Param("startTime") Date startTime,
                             @Param("endTime") Date endTime);

    /**
     * 查询指定商品 + 支付方式下最新的一笔未支付订单（V1 语义，legacy_status=NOTPAY），不加锁。
     * 用途：创建订单时在同维度 Redis 分布式锁内复用未支付订单。
     */
    OrderInfoPO selectLatestNoPayOrder(@Param("productId") Long productId,
                                       @Param("paymentType") String paymentType,
                                       @Param("orderStatus") String orderStatus,
                                       @Param("paymentAppId") Long paymentAppId);

    /**
     * 渠道下单成功 CAS：pay_status UNPAID → PAYING。
     */
    int casUnpaidToPaying(@Param("orderNo") String orderNo, @Param("fenceToken") Long fenceToken);

    /**
     * 支付成功 CAS：WAIT_PAY → ACTIVE，同时置 pay_status=PAID、实付金额、支付时间。
     */
    int casWaitPayToActive(@Param("orderNo") String orderNo, @Param("paidAmount") Integer paidAmount, @Param("fenceToken") Long fenceToken);

    /**
     * 超时关单 CAS：WAIT_PAY → CLOSED，pay_status 重置为 UNPAID，履约同步置 CANCELLED。
     */
    int casWaitPayToClosed(@Param("orderNo") String orderNo, @Param("fenceToken") Long fenceToken);

    /**
     * 订单层退款冻结（三层防线之订单层）：已退+冻结+本次 不超过实付。
     */
    int freezeOrderRefund(@Param("orderNo") String orderNo, @Param("amount") Integer amount, @Param("fenceToken") Long fenceToken);

    /**
     * 释放订单层退款冻结（拒绝/撤回）。
     */
    int releaseOrderRefundFreeze(@Param("orderNo") String orderNo, @Param("amount") Integer amount, @Param("fenceToken") Long fenceToken);

    /**
     * 订单层退款结转：冻结转已退。
     */
    int settleOrderRefund(@Param("orderNo") String orderNo, @Param("amount") Integer amount, @Param("fenceToken") Long fenceToken);

    /**
     * 按明细汇总回写订单退款汇总状态（幂等）。
     */
    int applyOrderRefundStatus(@Param("orderNo") String orderNo, @Param("fenceToken") Long fenceToken);
}
