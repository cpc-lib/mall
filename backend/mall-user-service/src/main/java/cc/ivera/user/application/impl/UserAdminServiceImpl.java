package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.PageVO;
import cc.ivera.user.application.UserAdminService;
import cc.ivera.user.application.dto.UserStatusRequest;
import cc.ivera.user.application.vo.UserVO;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.AuthSessionRepository;
import cc.ivera.user.domain.repository.UserAccountRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserAdminServiceImpl implements UserAdminService {
    private static final String VERSION_PREFIX = "auth:token_version:";

    private final UserAccountRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuthSessionRepository authSessionRepository;

    public UserAdminServiceImpl(UserAccountRepository userRepository, StringRedisTemplate redisTemplate,
                                AuthSessionRepository authSessionRepository) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.authSessionRepository = authSessionRepository;
    }

    @Override
    public PageVO<UserVO> list(int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String keywordLike = (keyword != null && !keyword.trim().isEmpty()) ? "%" + keyword.trim() + "%" : null;
        long total = userRepository.countAdminUsers(keywordLike);
        List<UserAccount> users = userRepository.listAdminUsers(keywordLike, safeSize, (long) (safePage - 1) * safeSize);
        List<UserVO> records = new ArrayList<>();
        for (UserAccount user : users) records.add(toRow(user));
        PageVO<UserVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(records);
        return result;
    }

    @Override
    public UserVO detail(Long id) {
        UserAccount user = userRepository.findById(id);
        if (user == null) throw new BizException("用户不存在");
        UserVO result = toRow(user);
        result.setOnline(!authSessionRepository.listByUser(user.getId()).isEmpty());
        return result;
    }

    @Override
    public UserVO setStatus(Long id, UserStatusRequest request) {
        String status = request == null ? null : request.getUserStatus();
        if (!CommonStatus.ENABLED.getType().equals(status) && !CommonStatus.DISABLED.getType().equals(status))
            throw new BizException("用户状态仅支持 ENABLED / DISABLED");
        UserAccount user = userRepository.findById(id);
        if (user == null) throw new BizException("用户不存在");
        if ("ROLE_ADMIN".equals(user.getRole())) throw new BizException("仅允许操作普通用户账号");
        if (!status.equals(user.getUserStatus())) {
            user.setUserStatus(status);
            userRepository.update(user);
        }
        if (CommonStatus.DISABLED.getType().equals(status)) {
            redisTemplate.opsForValue().increment(VERSION_PREFIX + user.getId());
            authSessionRepository.deleteAll(user.getId());
        }
        UserVO result = new UserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setRole(user.getRole());
        result.setUserStatus(user.getUserStatus());
        return result;
    }

    private UserVO toRow(UserAccount user) {
        UserVO result = new UserVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setRole(user.getRole());
        result.setUserStatus(user.getUserStatus());
        result.setCreateTime(user.getCreateTime());
        result.setUpdateTime(user.getUpdateTime());
        return result;
    }
}
