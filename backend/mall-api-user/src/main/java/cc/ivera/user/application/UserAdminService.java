package cc.ivera.user.application;

import cc.ivera.shared.web.PageVO;
import cc.ivera.user.application.dto.UserStatusRequest;
import cc.ivera.user.application.vo.UserVO;

public interface UserAdminService {
    PageVO<UserVO> list(int page, int size, String keyword);

    UserVO detail(Long id);

    UserVO setStatus(Long id, UserStatusRequest request);
}
