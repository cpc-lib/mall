package cc.ivera.user.interfaces;

import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.web.R;
import cc.ivera.user.application.AuthSessionService;
import cc.ivera.user.domain.model.AuthSession;
import cc.ivera.user.application.vo.AuthSessionVO;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端会话治理：按用户列出设备会话、踢单个设备、踢全部。
 */
@RestController
@RequestMapping("/api/admin/users/{userId}/sessions")
@CrossOrigin
public class AdminUserSessionController {

    private final AuthSessionService authSessionService;

    public AdminUserSessionController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @GetMapping
    public R<List<AuthSessionVO>> list(@PathVariable Long userId) {
        AuthPrincipal current = AuthContext.get();
        String currentSid = current == null ? null : current.getSid();
        List<AuthSessionVO> result = new ArrayList<>();
        for (AuthSession session : authSessionService.listSessionsByUser(userId)) {
            AuthSessionVO vo = new AuthSessionVO();
            vo.setSid(session.getSid());
            vo.setIp(session.getIp());
            vo.setUserAgent(session.getUserAgent());
            vo.setIssuedAt(session.getIssuedAt());
            vo.setLastSeenAt(session.getLastSeenAt());
            vo.setCurrent(session.getSid().equals(currentSid));
            result.add(vo);
        }
        return R.ok(result);
    }

    @DeleteMapping("/{sid}")
    public R<?> kick(@PathVariable Long userId, @PathVariable String sid) {
        authSessionService.kick(userId, sid);
        return R.ok().setMessage("该设备已下线");
    }

    @DeleteMapping
    public R<?> kickAll(@PathVariable Long userId) {
        authSessionService.kickAll(userId);
        return R.ok().setMessage("该用户全部会话已下线");
    }
}
