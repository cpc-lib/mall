package cc.ivera.refund.interfaces;

import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.application.dto.RefundRequest;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@CrossOrigin
@Tag(name = "退款申请")
@RestController
@Validated
public class RefundApplicationController {

    private final RefundApplicationService refundApplicationService;

    public RefundApplicationController(RefundApplicationService refundApplicationService) {
        this.refundApplicationService = refundApplicationService;
    }

    @Operation(summary = "申请退款")
    @PostMapping({
        "/api/refund-info/apply",
        "/api/wx-pay/refunds",
        "/api/ali-pay/trade/refund"
    })
    public R<?> apply(@Valid @RequestBody RefundRequest request) {
        refundApplicationService.createApplication(request.getOrderNo(), request.getRefundAmount(), request.getReason());
        return R.ok().setMessage("退款申请单创建成功，待审核");
    }

    @Operation(summary = "申请退款，兼容旧接口")
    @PostMapping({
        "/api/refund-info/apply/{orderNo}/{reason}",
        "/api/wx-pay/refunds/{orderNo}/{reason}",
        "/api/ali-pay/trade/refund/{orderNo}/{reason}"
    })
    public R<?> applyLegacy(
        @PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo,
        @PathVariable @NotBlank(message = "退款原因不能为空") @Size(max = 50, message = "退款原因长度不能超过50个字符") String reason) {
        refundApplicationService.createApplication(orderNo, null, reason);
        return R.ok().setMessage("退款申请单创建成功，待审核");
    }
}
