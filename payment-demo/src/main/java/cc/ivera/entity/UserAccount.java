package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_user")
public class UserAccount extends BaseEntity {
    private String username;
    private String passwordHash;
    private String passwordSalt;
    private String role;
    private String userStatus;
}
