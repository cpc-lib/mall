package cc.ivera.product.domain.policy;

import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ProductSpecGroup;
import cc.ivera.product.domain.model.ProductSpecValue;
import cc.ivera.product.domain.model.ProductSpu;
import cc.ivera.product.domain.model.ProductSpuAttr;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpuSpecPolicyTest {

    @Test
    void normalizeSpecMode_defaultsAndRejectsInvalid() {
        assertEquals(ProductSpu.SPEC_MODE_SINGLE, SpuSpecPolicy.normalizeSpecMode(null));
        assertEquals(ProductSpu.SPEC_MODE_SINGLE, SpuSpecPolicy.normalizeSpecMode("  "));
        assertEquals(ProductSpu.SPEC_MODE_MULTI, SpuSpecPolicy.normalizeSpecMode(" multi "));
        assertThrows(BizException.class, () -> SpuSpecPolicy.normalizeSpecMode("HYBRID"));
    }

    @Test
    void validateAttributes_rejectsDuplicateOrBlankNames() {
        ProductSpuAttr a1 = new ProductSpuAttr();
        a1.setAttrName(" 品牌 ");
        a1.setAttrValue(" Ivera ");
        ProductSpuAttr a2 = new ProductSpuAttr();
        a2.setAttrName("品牌");
        a2.setAttrValue("其他");

        assertThrows(BizException.class, () -> SpuSpecPolicy.validateAttributes(List.of(a1, a2)));

        ProductSpuAttr valid = new ProductSpuAttr();
        valid.setAttrName(" 交付形式 ");
        valid.setAttrValue(" 录播视频 ");
        SpuSpecPolicy.validateAttributes(List.of(valid));
        assertEquals("交付形式", valid.getAttrName());
        assertEquals("录播视频", valid.getAttrValue());
    }

    @Test
    void validateSpecGroups_enforcesSingleAndMultiRules() {
        ProductSpecGroup g = group(1L, "版本", List.of("标准版", "进阶版"));
        assertThrows(BizException.class, () -> SpuSpecPolicy.validateSpecGroups(ProductSpu.SPEC_MODE_SINGLE, List.of(g)));
        assertThrows(BizException.class, () -> SpuSpecPolicy.validateSpecGroups(ProductSpu.SPEC_MODE_MULTI, List.of()));

        ProductSpecGroup dupVal = group(2L, "有效期", List.of("1年", "1年"));
        assertThrows(BizException.class, () -> SpuSpecPolicy.validateSpecGroups(ProductSpu.SPEC_MODE_MULTI, List.of(dupVal)));

        assertDoesNotThrow(() -> SpuSpecPolicy.validateSpecGroups(ProductSpu.SPEC_MODE_MULTI, List.of(g)));
    }

    @Test
    void buildSpecKeyTextJson_isOrderIndependentForKeyAndOrderedForText() {
        ProductSpecGroup g1 = group(20L, "版本", List.of("标准版"));
        ProductSpecGroup g2 = group(10L, "有效期", List.of("永久有效"));

        // 即使输入顺序相反，specKey 按 groupId 升序归一化
        Map<Long, Long> mapA = new LinkedHashMap<>();
        mapA.put(20L, 201L);
        mapA.put(10L, 101L);
        Map<Long, Long> mapB = new LinkedHashMap<>();
        mapB.put(10L, 101L);
        mapB.put(20L, 201L);

        assertEquals("10:101;20:201", SpuSpecPolicy.buildSpecKey(mapA));
        assertEquals(SpuSpecPolicy.buildSpecKey(mapA), SpuSpecPolicy.buildSpecKey(mapB));
        assertEquals(SpuSpecPolicy.DEFAULT_SPEC_KEY, SpuSpecPolicy.buildSpecKey(Map.of()));

        Map<Long, String> valueTexts = Map.of(20L, "标准版", 10L, "永久有效");
        assertEquals("标准版 / 永久有效", SpuSpecPolicy.buildSpecText(List.of(g1, g2), valueTexts));
        assertEquals("[{\"name\":\"版本\",\"value\":\"标准版\"},{\"name\":\"有效期\",\"value\":\"永久有效\"}]",
            SpuSpecPolicy.buildSpecJson(List.of(g1, g2), valueTexts));
    }

    @Test
    void computeSummary_prefersEnabledSkusAndLowestPrice() {
        Product disabledCheap = Product.create("课程", 100, 10);
        disabledCheap.setId(1L);
        disabledCheap.setProductStatus(CommonStatus.DISABLED.getType());

        Product enabledMid = Product.create("课程", 300, 10);
        enabledMid.setId(2L);

        Product enabledHigh = Product.create("课程", 500, 10);
        enabledHigh.setId(3L);

        SpuSpecPolicy.PriceSummary summary = SpuSpecPolicy.computeSummary(List.of(disabledCheap, enabledHigh, enabledMid));
        assertEquals(300, summary.minPrice());
        assertEquals(500, summary.maxPrice());
        assertEquals(2L, summary.defaultSkuId());
    }

    @Test
    void validateSpuEnableable_requiresAtLeastOneEnabledSku() {
        Product disabled = Product.create("课程", 100, 10);
        disabled.setProductStatus(CommonStatus.DISABLED.getType());
        assertThrows(BizException.class, () -> SpuSpecPolicy.validateSpuEnableable(List.of(disabled)));

        Product enabled = Product.create("课程", 200, 10);
        assertDoesNotThrow(() -> SpuSpecPolicy.validateSpuEnableable(List.of(disabled, enabled)));
    }

    private static ProductSpecGroup group(Long id, String name, List<String> vals) {
        ProductSpecGroup g = new ProductSpecGroup();
        g.setId(id);
        g.setSpecName(name);
        g.setValues(vals.stream().map(v -> {
            ProductSpecValue sv = new ProductSpecValue();
            sv.setSpecValue(v);
            return sv;
        }).toList());
        return g;
    }
}
