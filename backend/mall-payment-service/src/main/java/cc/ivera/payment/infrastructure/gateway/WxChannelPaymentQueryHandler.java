package cc.ivera.payment.infrastructure.gateway;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelPaymentQueryHandler;
import cc.ivera.payment.application.wxpay.WxPayOrderFacade;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.payment.application.vo.WxPayStatusVO;
import cc.ivera.shared.infrastructure.util.JsonUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 微信支付查单适配器：将 WxPayStatusVO 转换为统一渠道查单结果。
 */
@Component
public class WxChannelPaymentQueryHandler implements ChannelPaymentQueryHandler {

    private final WxPayOrderFacade wxPayOrderFacade;

    private final OrderPaymentApi orderInfoService;

    public WxChannelPaymentQueryHandler(WxPayOrderFacade wxPayOrderFacade, OrderPaymentApi orderInfoService) {
        this.wxPayOrderFacade = wxPayOrderFacade;
        this.orderInfoService = orderInfoService;
    }

    @Override
    public ChannelOrderQueryVO queryAndSyncStatus(String orderNo) {
        WxPayStatusVO wx = wxPayOrderFacade.queryPaymentStatus(orderNo);
        ChannelOrderQueryVO vo = new ChannelOrderQueryVO();
        vo.setOrderNo(orderNo);
        vo.setChannelCode(PaymentConfigGateway.CHANNEL_WXPAY);
        vo.setChannelTradeState(wx.getTradeState());
        vo.setChannelTradeStateDesc(wx.getTradeStateDesc());
        vo.setLocalOrderStatusBefore(wx.getLocalStatusBefore());
        vo.setLocalOrderStatusAfter(wx.getLocalStatus());
        OrderInfo after = orderInfoService.getOrderByOrderNo(orderNo);
        vo.setLocalPayStatusAfter(after == null ? null : after.getPayStatus());
        vo.setSynced(!Objects.equals(wx.getLocalStatusBefore(), wx.getLocalStatus()));
        vo.setChannelRawBody(wx.getWxPayResult() == null ? null : JsonUtils.toJson(wx.getWxPayResult()));
        return vo;
    }
}
