package cc.ivera.user.interfaces;

import cc.ivera.shared.web.R;
import cc.ivera.user.application.PasswordResetRequestService;
import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.interfaces.dto.PasswordResetHandleRequest;
import cc.ivera.user.interfaces.dto.PasswordResetRejectRequest;
import cc.ivera.user.interfaces.vo.PasswordResetHandleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 管理员找回密码申请管理：查看申请列表、处理（生成一次性随机密码）、拒绝。
 */
@RestController
@RequestMapping("/api/admin/password-reset-requests")
@CrossOrigin
@Api(tags = "管理员密码重置申请API")
public class AdminPasswordResetRequestController {
    private final PasswordResetRequestService passwordResetRequestService;

    public AdminPasswordResetRequestController(PasswordResetRequestService passwordResetRequestService) {
        this.passwordResetRequestService = passwordResetRequestService;
    }

    @ApiOperation("密码重置申请列表（待处理优先）")
    @GetMapping
    public R<List<PasswordResetRequest>> list() {
        return R.ok(passwordResetRequestService.list());
    }

    @ApiOperation("处理申请：重置为系统生成的一次性随机密码")
    @PostMapping("/{id}/handle")
    public R<PasswordResetHandleVO> handle(@PathVariable Long id, @RequestBody(required = false) @Valid PasswordResetHandleRequest request) {
        return R.ok(passwordResetRequestService.handle(id, request == null ? new PasswordResetHandleRequest() : request));
    }

    @ApiOperation("拒绝申请")
    @PostMapping("/{id}/reject")
    public R<?> reject(@PathVariable Long id, @RequestBody(required = false) @Valid PasswordResetRejectRequest request) {
        passwordResetRequestService.reject(id, request == null ? new PasswordResetRejectRequest() : request);
        return R.ok().setMessage("已拒绝该申请");
    }
}
