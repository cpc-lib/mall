package cc.ivera.payment.infrastructure.gateway;

import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;
import cc.ivera.payment.application.wxpay.WxPayRefundFacade;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.shared.domain.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 渠道退款契约的进程内实现：按渠道路由到支付宝/微信退款实现。
 * 支付服务拆分后，本类由远程客户端替换，契约不变。
 */
@Component
@RequiredArgsConstructor
public class PaymentChannelRefundInternalApiAdapter implements PaymentChannelRefundApi {

    private final AliPayService aliPayService;

    private final WxPayRefundFacade wxPayRefundFacade;

    @Override
    public ChannelRefundResult executeRefund(ChannelRefundCommand command) {
        if (command == null) {
            throw new BizException("退款申请单不能为空");
        }
        if (PayType.WXPAY.getType().equals(command.getChannel())) {
            return wxPayRefundFacade.executeRefund(command);
        }
        if (PayType.ALIPAY.getType().equals(command.getChannel())) {
            return aliPayService.executeRefund(command);
        }
        throw new BizException("不支持的支付类型：" + command.getChannel());
    }

    @Override
    public ChannelRefundStatus queryRefund(ChannelRefundQuery query) {
        if (query == null) {
            throw new BizException("退款查询参数不能为空");
        }
        if (PayType.WXPAY.getType().equals(query.getChannel())) {
            return wxPayRefundFacade.queryRefund(query);
        }
        if (PayType.ALIPAY.getType().equals(query.getChannel())) {
            return aliPayService.queryRefund(query);
        }
        throw new BizException("不支持的支付类型：" + query.getChannel());
    }

    @Override
    public ChannelRefundSyncResult queryRefundStatusForSync(ChannelRefundQuery query) {
        if (query == null) {
            throw new BizException("退款查询参数不能为空");
        }
        if (PayType.WXPAY.getType().equals(query.getChannel())) {
            return wxPayRefundFacade.queryRefundStatusForSync(query);
        }
        if (PayType.ALIPAY.getType().equals(query.getChannel())) {
            return aliPayService.queryRefundStatusForSync(query);
        }
        throw new BizException("不支持的支付类型：" + query.getChannel());
    }

    @Override
    public List<ChannelRefundSyncResult> queryOrderRefundsForSync(String orderNo, String channel) {
        if (PayType.WXPAY.getType().equals(channel)) {
            return wxPayRefundFacade.queryOrderRefundsForSync(orderNo, channel);
        }
        throw new BizException("不支持的支付类型：" + channel);
    }

    @Override
    public ChannelRefundSyncResult processRefundNotify(Map<String, Object> bodyMap) {
        return wxPayRefundFacade.processRefundNotify(bodyMap);
    }
}
