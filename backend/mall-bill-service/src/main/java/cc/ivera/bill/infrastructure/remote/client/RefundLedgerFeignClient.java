package cc.ivera.bill.infrastructure.remote.client;

import cc.ivera.infra.feign.InternalFeignConfiguration;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.shared.web.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 退款服务内部契约客户端：路径与 {@code RefundInternalController} 保持一一对应。
 */
@FeignClient(name = "mall-refund-service", url = "${refund.service.base-url:}",
    configuration = InternalFeignConfiguration.class)
public interface RefundLedgerFeignClient {

    @GetMapping("/internal/refund/ledger")
    R<List<RefundOrder>> listByRefundNos(@RequestParam("refundNos") String refundNos);

    @GetMapping("/internal/refund/ledger/success")
    R<List<RefundOrder>> listSuccessBySuccessTimeRange(@RequestParam("start") long start,
                                                       @RequestParam("end") long end);
}
