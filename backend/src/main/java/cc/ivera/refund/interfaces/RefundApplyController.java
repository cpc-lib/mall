package cc.ivera.refund.interfaces;

import cc.ivera.refund.application.RefundOrderService;
import cc.ivera.refund.interfaces.dto.RefundApplyRequest;
import cc.ivera.refund.interfaces.dto.RefundApplyUpdateRequest;
import cc.ivera.refund.interfaces.dto.RefundRejectRequest;
import cc.ivera.refund.interfaces.vo.RefundApplyVO;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 退款申请用户端（V2）：创建/编辑/撤回/查询。
 * 响应中的退款金额为服务端按尾差规则计算值（RefundPolicy），前端展示以服务端为准。
 * accept/reject 为管理员操作（拦截器按 /accept、/reject 后缀做管理员校验），逻辑同 /api/admin/refund。
 */
@RestController
@RequestMapping("/api/refund-applies")
@CrossOrigin
public class RefundApplyController {
    private final RefundOrderService service;

    public RefundApplyController(RefundOrderService service) {
        this.service = service;
    }

    @PostMapping
    public R<RefundApplyVO> create(@Valid @RequestBody RefundApplyRequest r) {
        return R.ok(service.create(AuthContext.userId(), r));
    }

    @PutMapping("/{refundNo}")
    public R<RefundApplyVO> update(@PathVariable String refundNo, @Valid @RequestBody RefundApplyUpdateRequest r) {
        return R.ok(service.update(AuthContext.userId(), refundNo, r));
    }

    @DeleteMapping("/{refundNo}")
    public R<?> cancel(@PathVariable String refundNo) {
        service.cancel(AuthContext.userId(), refundNo);
        return R.ok().setMessage("退款申请已撤销，冻结额度已释放");
    }

    @GetMapping
    public R<List<RefundApplyVO>> mine() {
        return R.ok(service.listForUser(AuthContext.userId()));
    }

    @GetMapping("/admin/all")
    public R<List<RefundApplyVO>> all() {
        return R.ok(service.listAll());
    }

    @PostMapping("/{refundNo}/accept")
    public R<?> accept(@PathVariable String refundNo, @RequestBody(required = false) RefundRejectRequest r) {
        service.accept(refundNo, r == null ? null : r.getRemark());
        return R.ok().setMessage("已受理");
    }

    @PostMapping("/{refundNo}/reject")
    public R<?> reject(@PathVariable String refundNo, @RequestBody(required = false) RefundRejectRequest r) {
        service.reject(refundNo, r == null ? null : r.getRemark());
        return R.ok().setMessage("已拒绝并释放冻结额度");
    }
}
