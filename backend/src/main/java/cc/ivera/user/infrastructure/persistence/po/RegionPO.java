package cc.ivera.user.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_region")
public class RegionPO {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private Integer sort;
}
