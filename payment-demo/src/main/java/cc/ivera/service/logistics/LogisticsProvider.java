package cc.ivera.service.logistics;

import cc.ivera.entity.OrderInfo;

/**
 * 物流商对接接口（V2）。
 * 当前实现为模拟物流商（MockLogisticsProvider）；
 * 对接真实物流商时仅需替换实现，不动交易/履约状态机。
 */
public interface LogisticsProvider {

    /**
     * 创建运单（发货）。
     *
     * @return 运单号
     */
    String createWaybill(OrderInfo order);

    /**
     * 查询渠道侧物流状态（模拟实现按运单创建时间推移返回）。
     *
     * @return 物流商侧状态描述
     */
    String queryStatus(String trackingNo);
}
