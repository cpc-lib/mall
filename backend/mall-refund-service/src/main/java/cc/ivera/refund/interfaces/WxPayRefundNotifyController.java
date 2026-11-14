package cc.ivera.refund.interfaces;

import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.payment.interfaces.support.WxPayNotifyHandler;
import cc.ivera.refund.application.RefundApplicationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 微信退款结果通知：验签后调用支付服务处理渠道退款，再由退款域回写 t_refund_*。
 */
@CrossOrigin
@RestController
@RequestMapping("/api/wx-pay")
@Tag(name = "退款通知")
@Slf4j
@Validated
public class WxPayRefundNotifyController {

    private final WxPayNotifyHandler wxPayNotifyHandler;

    private final PaymentChannelRefundApi paymentChannelRefundApi;

    private final RefundApplicationService refundApplicationService;

    public WxPayRefundNotifyController(WxPayNotifyHandler wxPayNotifyHandler,
                                       PaymentChannelRefundApi paymentChannelRefundApi,
                                       RefundApplicationService refundApplicationService) {
        this.wxPayNotifyHandler = wxPayNotifyHandler;
        this.paymentChannelRefundApi = paymentChannelRefundApi;
        this.refundApplicationService = refundApplicationService;
    }

    @Operation(summary = "退款结果通知")
    @PostMapping("/refunds/notify")
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response) {
        log.info("退款通知执行");
        return wxPayNotifyHandler.handle(request, response,
            bodyMap -> refundApplicationService.applyRefundSync(paymentChannelRefundApi.processRefundNotify(bodyMap)),
            "处理微信退款通知失败");
    }
}
