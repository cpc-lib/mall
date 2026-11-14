package cc.ivera.refund.interfaces;

import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.vo.StringResultVO;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.refund.application.RefundInfoService;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 退款查询：以本地退款单为准，渠道状态通过支付服务契约查询。
 */
@CrossOrigin
@RestController
@Tag(name = "退款查询")
@Slf4j
@Validated
public class PaymentRefundQueryController {

    private final RefundInfoService refundInfoService;

    private final PaymentChannelRefundApi paymentChannelRefundApi;

    public PaymentRefundQueryController(RefundInfoService refundInfoService,
                                        PaymentChannelRefundApi paymentChannelRefundApi) {
        this.refundInfoService = refundInfoService;
        this.paymentChannelRefundApi = paymentChannelRefundApi;
    }

    @Operation(summary = "查询退款：测试用")
    @GetMapping("/api/wx-pay/query-refund/{refundNo}")
    public R<StringResultVO> queryWxRefund(@PathVariable @NotBlank(message = "退款单号不能为空") @Size(max = 50, message = "退款单号长度不能超过50个字符") String refundNo) {
        log.info("查询退款");
        return queryRefund(refundNo, PayType.WXPAY.getType());
    }

    @Operation(summary = "查询退款：测试用")
    @GetMapping("/api/ali-pay/trade/fastpay/refund/{refundNo}")
    public R<StringResultVO> queryAliRefund(@PathVariable @NotBlank(message = "退款单号不能为空") @Size(max = 50, message = "退款单号长度不能超过50个字符") String refundNo) {
        log.info("查询退款");
        return queryRefund(refundNo, PayType.ALIPAY.getType());
    }

    private R<StringResultVO> queryRefund(String refundNo, String channel) {
        RefundInfo refundInfo = refundInfoService.getByRefundNo(refundNo);
        if (refundInfo == null) {
            throw new BizException("退款单不存在");
        }
        ChannelRefundStatus status = paymentChannelRefundApi.queryRefund(
            new ChannelRefundQuery(refundNo, refundInfo.getOrderNo(), channel));
        StringResultVO vo = new StringResultVO();
        vo.setResult(status == null ? null : status.getRawBody());
        return R.ok(vo).setMessage("查询成功");
    }
}
