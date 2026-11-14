package cc.ivera.user.domain.model;

import lombok.Data;

/**
 * 省市区三级区域（t_region）：公共只读基础数据。
 */
@Data
public class Region {
    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private Integer level;
    private Integer sort;
}
