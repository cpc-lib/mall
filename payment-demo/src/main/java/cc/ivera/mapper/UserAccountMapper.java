package cc.ivera.mapper;

import cc.ivera.entity.UserAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserAccountMapper extends BaseMapper<UserAccount> {

    /**
     * 管理员用户列表总数：username 关键字模糊（keywordLike 为 null 时查全量）。
     */
    Long countAdminUsers(@Param("keywordLike") String keywordLike);

    /**
     * 管理员用户列表分页：username 关键字模糊可选，按 id 升序，LIMIT/OFFSET 手动分页。
     */
    List<UserAccount> selectAdminUsers(@Param("keywordLike") String keywordLike,
                                       @Param("limit") int limit,
                                       @Param("offset") long offset);
}
