package cc.ivera.refund.interfaces;

import cc.ivera.refund.application.RefundOrderService;
import cc.ivera.refund.application.dto.PriceAdjustmentRequest;
import cc.ivera.refund.application.dto.RefundRejectRequest;
import cc.ivera.refund.application.dto.RefundApplyVO;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 管理员退款处理（V2）：拒绝 / 退货签收确认 / 失败重试 / 差价退款。
 * 受理入口保留在 /api/refund-applies/{refundNo}/accept（拦截器已按管理员校验）。
 */
@RestController
@RequestMapping("/api/admin/refund")
@CrossOrigin
@Validated
@Tag(name = "管理员退款API")
public class AdminRefundOrderController {

    private final RefundOrderService refundOrderService;

    public AdminRefundOrderController(RefundOrderService refundOrderService) {
        this.refundOrderService = refundOrderService;
    }

    @Operation(summary = "拒绝退款申请（释放冻结额度）")
    @PostMapping("/{refundNo}/reject")
    public R<?> reject(@PathVariable String refundNo,
                       @RequestBody(required = false) RefundRejectRequest r) {
        refundOrderService.reject(refundNo, r == null ? null : r.getRemark());
        return R.ok().setMessage("已拒绝并释放冻结额度");
    }

    @Operation(summary = "退货签收质检确认（RETURN_AND_REFUND：补库存并发起渠道退款）")
    @PostMapping("/{refundNo}/confirm-return")
    public R<?> confirmReturn(@PathVariable String refundNo,
                              @RequestBody(required = false) RefundRejectRequest r) {
        refundOrderService.confirmReturn(refundNo, r == null ? null : r.getRemark());
        return R.ok().setMessage("退货签收确认成功，库存已回补并发起渠道退款");
    }

    @Operation(summary = "重试失败退款（FAILED→REFUNDING）")
    @PostMapping("/{refundNo}/retry")
    public R<?> retry(@PathVariable String refundNo) {
        refundOrderService.retry(refundNo);
        return R.ok().setMessage("已重新发起渠道退款");
    }

    @Operation(summary = "主动查询渠道退款状态并同步 V2 状态")
    @PostMapping("/{refundNo}/query-status")
    public R<RefundApplyVO> queryRefundStatus(@PathVariable String refundNo) {
        return R.ok(refundOrderService.queryRefundStatus(refundNo)).setMessage("退款状态查询完成");
    }

    @Operation(summary = "差价退款（管理员手填金额，受订单可退额度约束）")
    @PostMapping("/price-adjustment")
    public R<RefundApplyVO> priceAdjustment(@Valid @RequestBody PriceAdjustmentRequest req) {
        return R.ok(refundOrderService.createPriceAdjustment(req.getOrderNo(), req.getAmount(), req.getReason()))
            .setMessage("差价退款申请已创建并冻结额度");
    }

    @Operation(summary = "按订单号查询退款单列表（订单详情页展示）")
    @GetMapping("/order/{orderNo}")
    public R<List<RefundApplyVO>> listByOrderNo(@PathVariable String orderNo) {
        return R.ok(refundOrderService.listByOrderNo(orderNo));
    }
}
