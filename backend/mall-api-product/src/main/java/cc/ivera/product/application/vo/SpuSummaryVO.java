package cc.ivera.product.application.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * SPU 分页卡片摘要 VO（用户端与管理端共用）。
 */
@Data
public class SpuSummaryVO {

    private Long spuId;
    private String spuCode;
    private String title;
    private String subtitle;
    private String categoryName;
    private String brandName;
    private String mainImage;
    private String specMode;
    private String spuStatus;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer totalAvailableStock;
    private Integer totalLockedStock;
    private Integer totalSoldStock;
    private Integer totalLostStock;
    private Long defaultSkuId;
    private Integer skuCount;
    private List<String> attrSummary = new ArrayList<>();
    private Date createTime;
    private Date updateTime;
}
