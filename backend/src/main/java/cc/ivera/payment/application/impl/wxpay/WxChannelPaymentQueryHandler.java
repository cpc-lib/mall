package cc.ivera.payment.application.impl.wxpay;

import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.ChannelPaymentQueryHandler;
import cc.ivera.order.application.OrderInfoService;
import cc.ivera.payment.application.wxpay.WxPayOrderFacade;
import cc.ivera.shared.infrastructure.util.JsonUtils;
import cc.ivera.payment.interfaces.vo.ChannelOrderQueryVO;
import cc.ivera.payment.interfaces.vo.WxPayStatusVO;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 微信支付查单适配器：将 WxPayStatusVO 转换为统一渠道查单结果。
 */
@Component
public class WxChannelPaymentQueryHandler implements ChannelPaymentQueryHandler {

    private final WxPayOrderFacade wxPayOrderFacade;

    private final OrderInfoService orderInfoService;

    public WxChannelPaymentQueryHandler(WxPayOrderFacade wxPayOrderFacade, OrderInfoService orderInfoService) {
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
