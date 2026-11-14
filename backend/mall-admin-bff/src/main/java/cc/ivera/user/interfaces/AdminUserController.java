package cc.ivera.user.interfaces;

import cc.ivera.shared.web.PageVO;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.UserAdminService;
import cc.ivera.user.application.dto.UserStatusRequest;
import cc.ivera.user.application.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员用户管理：用户列表（分页+搜索，仅基础状态）+ 用户详情（含在线状态）+ 禁用/启用。
 */
@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin
@Tag(name = "管理员用户管理API")
public class AdminUserController {
    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @Operation(summary = "用户列表（分页 + 用户名关键字搜索，不含在线状态）")
    @GetMapping
    public R<PageVO<UserVO>> list(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String keyword) {
        return R.ok(userAdminService.list(page, size, keyword));
    }

    @Operation(summary = "用户详情（含在线状态）")
    @GetMapping("/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(userAdminService.detail(id));
    }

    @Operation(summary = "用户状态切换（禁用即强制下线）")
    @PutMapping("/{id}/status")
    public R<UserVO> setStatus(@PathVariable Long id, @RequestBody UserStatusRequest body) {
        return R.ok(userAdminService.setStatus(id, body));
    }
}
