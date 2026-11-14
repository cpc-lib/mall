package cc.ivera.refund.interfaces;

import cc.ivera.refund.application.RefundOrderService;
import cc.ivera.refund.application.dto.RefundApplyVO;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 已付款未发货取消入口：写入退款域，避免 BFF 持有退款写路径。
 */
@RestController
@RequestMapping("/api/order")
@CrossOrigin
@Validated
@Tag(name = "订单取消API")
public class RefundOrderCancelController {

    private final RefundOrderService refundOrderService;

    public RefundOrderCancelController(RefundOrderService refundOrderService) {
        this.refundOrderService = refundOrderService;
    }

    @Operation(summary = "已付款未发货取消订单（创建未发货取消退款单，走受理链路）")
    @PostMapping("/{orderNo}/cancel")
    public R<RefundApplyVO> cancelPaidOrder(@PathVariable String orderNo) {
        return R.ok(refundOrderService.cancelPaidOrder(AuthContext.userId(), orderNo))
            .setMessage("取消申请已提交，退款受理后自动原路退回");
    }
}
