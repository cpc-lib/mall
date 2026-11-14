package cc.ivera.user.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_user")
public class UserAccountPO extends BaseEntity {
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private String role;
    private String userStatus;
}
