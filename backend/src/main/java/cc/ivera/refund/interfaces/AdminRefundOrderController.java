package cc.ivera.refund.interfaces;

import cc.ivera.refund.application.RefundOrderService;
import cc.ivera.refund.interfaces.dto.PriceAdjustmentRequest;
import cc.ivera.refund.interfaces.dto.RefundRejectRequest;
import cc.ivera.refund.interfaces.vo.RefundApplyVO;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 管理员退款处理（V2）：拒绝 / 退货签收确认 / 失败重试 / 差价退款。
 * 受理入口保留在 /api/refund-applies/{refundNo}/accept（拦截器已按管理员校验）。
 */
@RestController
@RequestMapping("/api/admin/refund")
@CrossOrigin
@Validated
@Api(tags = "管理员退款API")
public class AdminRefundOrderController {

    private final RefundOrderService refundOrderService;

    public AdminRefundOrderController(RefundOrderService refundOrderService) {
        this.refundOrderService = refundOrderService;
    }

    @ApiOperation("拒绝退款申请（释放冻结额度）")
    @PostMapping("/{refundNo}/reject")
    public R<?> reject(@PathVariable String refundNo,
                       @RequestBody(required = false) RefundRejectRequest r) {
        refundOrderService.reject(refundNo, r == null ? null : r.getRemark());
        return R.ok().setMessage("已拒绝并释放冻结额度");
    }

    @ApiOperation("退货签收质检确认（RETURN_AND_REFUND：补库存并发起渠道退款）")
    @PostMapping("/{refundNo}/confirm-return")
    public R<?> confirmReturn(@PathVariable String refundNo,
                              @RequestBody(required = false) RefundRejectRequest r) {
        refundOrderService.confirmReturn(refundNo, r == null ? null : r.getRemark());
        return R.ok().setMessage("退货签收确认成功，库存已回补并发起渠道退款");
    }

    @ApiOperation("重试失败退款（FAILED→REFUNDING）")
    @PostMapping("/{refundNo}/retry")
    public R<?> retry(@PathVariable String refundNo) {
        refundOrderService.retry(refundNo);
        return R.ok().setMessage("已重新发起渠道退款");
    }

    @ApiOperation("主动查询渠道退款状态并同步 V2 状态")
    @PostMapping("/{refundNo}/query-status")
    public R<RefundApplyVO> queryRefundStatus(@PathVariable String refundNo) {
        return R.ok(refundOrderService.queryRefundStatus(refundNo)).setMessage("退款状态查询完成");
    }

    @ApiOperation("差价退款（管理员手填金额，受订单可退额度约束）")
    @PostMapping("/price-adjustment")
    public R<RefundApplyVO> priceAdjustment(@Valid @RequestBody PriceAdjustmentRequest req) {
        return R.ok(refundOrderService.createPriceAdjustment(req.getOrderNo(), req.getAmount(), req.getReason()))
            .setMessage("差价退款申请已创建并冻结额度");
    }
}
