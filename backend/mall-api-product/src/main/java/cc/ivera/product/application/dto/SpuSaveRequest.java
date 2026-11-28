package cc.ivera.product.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建 / 编辑 SPU（含基础展示属性、销售规格组与 SKU 矩阵）请求体。
 */
@Data
public class SpuSaveRequest {

    @Size(max = 64, message = "SPU编码不能超过64个字符")
    private String spuCode;

    @NotBlank(message = "商品标题不能为空")
    @Size(max = 256, message = "商品标题不能超过256个字符")
    private String title;

    @Size(max = 256, message = "商品副标题不能超过256个字符")
    private String subtitle;

    @Size(max = 64, message = "分类名称不能超过64个字符")
    private String categoryName;

    @Size(max = 64, message = "品牌名称不能超过64个字符")
    private String brandName;

    @Size(max = 512, message = "主图地址不能超过512个字符")
    private String mainImage;

    private String description;

    /**
     * SINGLE（单规格）或 MULTI（多规格），默认 SINGLE。
     */
    private String specMode = "SINGLE";

    /**
     * SPU 级基础展示属性（可选，如品牌、材质、交付形式）。
     */
    @Valid
    private List<AttrItem> attrs = new ArrayList<>();

    /**
     * 销售规格维度定义（MULTI 模式下必填，1~3 组）。
     */
    @Valid
    private List<SpecGroupItem> specGroups = new ArrayList<>();

    /**
     * SKU 列表（至少 1 条；SINGLE 模式恰好 1 条）。
     */
    @Valid
    @NotEmpty(message = "至少需要配置一个 SKU")
    private List<SkuItem> skus = new ArrayList<>();

    @Data
    public static class AttrItem {
        @NotBlank(message = "属性名不能为空")
        @Size(max = 64, message = "属性名不能超过64个字符")
        private String attrName;

        @NotBlank(message = "属性值不能为空")
        @Size(max = 256, message = "属性值不能超过256个字符")
        private String attrValue;

        private Integer sortOrder = 0;
    }

    @Data
    public static class SpecGroupItem {
        @NotBlank(message = "规格组名称不能为空")
        @Size(max = 64, message = "规格组名称不能超过64个字符")
        private String specName;

        @NotEmpty(message = "规格值列表不能为空")
        private List<String> values = new ArrayList<>();

        private Integer sortOrder = 0;
    }

    @Data
    public static class SpecPair {
        @NotBlank(message = "规格名不能为空")
        private String specName;

        @NotBlank(message = "规格值不能为空")
        private String specValue;
    }

    @Data
    public static class SkuItem {
        private Long skuId;

        @Size(max = 64, message = "SKU编码不能超过64个字符")
        private String skuCode;

        @NotNull(message = "SKU价格不能为空")
        @Min(value = 1, message = "SKU价格必须大于0分")
        private Integer price;

        /**
         * 仅新建 SKU 时用于初始化可用库存；编辑已有 SKU 时忽略（库存调整走专用接口）。
         */
        @Min(value = 0, message = "初始库存不能为负数")
        private Integer stock = 0;

        private String productStatus = "ENABLED";

        @Valid
        private List<SpecPair> specs = new ArrayList<>();
    }
}
