package cc.ivera.refund.interfaces;

import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 退款服务内部接口：账单上下文只读平台退款账，路由不经过网关，由内部 Token 认证。
 */
@RestController
@RequestMapping("/internal/refund")
public class RefundInternalController {

    private final RefundOrderRepository refundOrderRepository;

    public RefundInternalController(RefundOrderRepository refundOrderRepository) {
        this.refundOrderRepository = refundOrderRepository;
    }

    @GetMapping("/ledger")
    public R<List<RefundOrder>> listByRefundNos(@RequestParam List<String> refundNos) {
        return R.ok(refundOrderRepository.listByRefundNos(refundNos));
    }

    @GetMapping("/ledger/success")
    public R<List<RefundOrder>> listSuccessBySuccessTimeRange(@RequestParam long start, @RequestParam long end) {
        return R.ok(refundOrderRepository.listSuccessBySuccessTimeRange(new Date(start), new Date(end)));
    }
}
