package cc.ivera.product.domain.policy;

import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ProductSpecGroup;
import cc.ivera.product.domain.model.ProductSpecValue;
import cc.ivera.product.domain.model.ProductSpu;
import cc.ivera.product.domain.model.ProductSpuAttr;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SPU / 销售规格 / 基础展示属性纯领域策略（零 Spring / MyBatis 依赖）。
 */
public final class SpuSpecPolicy {

    public static final int MAX_ATTR_COUNT = 30;
    public static final int MAX_SPEC_GROUP_COUNT = 3;
    public static final int MAX_SPEC_VALUE_PER_GROUP = 20;
    public static final String DEFAULT_SPEC_KEY = "DEFAULT";

    private SpuSpecPolicy() {
    }

    public static String normalizeSpecMode(String specMode) {
        if (specMode == null || specMode.trim().isEmpty()) {
            return ProductSpu.SPEC_MODE_SINGLE;
        }
        String normalized = specMode.trim().toUpperCase();
        if (!ProductSpu.SPEC_MODE_SINGLE.equals(normalized) && !ProductSpu.SPEC_MODE_MULTI.equals(normalized)) {
            throw new BizException("规格模式仅支持 SINGLE / MULTI");
        }
        return normalized;
    }

    public static void validateAttributes(List<ProductSpuAttr> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return;
        }
        if (attrs.size() > MAX_ATTR_COUNT) {
            throw new BizException("单个商品基础展示属性最多 " + MAX_ATTR_COUNT + " 项");
        }
        Set<String> seenNames = new HashSet<>();
        for (ProductSpuAttr attr : attrs) {
            if (attr == null || attr.getAttrName() == null || attr.getAttrName().trim().isEmpty()) {
                throw new BizException("基础属性名不能为空");
            }
            if (attr.getAttrValue() == null || attr.getAttrValue().trim().isEmpty()) {
                throw new BizException("基础属性值不能为空");
            }
            String name = attr.getAttrName().trim();
            if (!seenNames.add(name)) {
                throw new BizException("同一商品下不能存在重复的基础属性名：" + name);
            }
            attr.setAttrName(name);
            attr.setAttrValue(attr.getAttrValue().trim());
        }
    }

    public static void validateSpecGroups(String specMode, List<ProductSpecGroup> groups) {
        if (ProductSpu.SPEC_MODE_SINGLE.equals(specMode)) {
            if (groups != null && !groups.isEmpty()) {
                throw new BizException("单规格商品不允许配置销售规格组");
            }
            return;
        }
        if (groups == null || groups.isEmpty()) {
            throw new BizException("多规格商品至少需要配置 1 组销售规格");
        }
        if (groups.size() > MAX_SPEC_GROUP_COUNT) {
            throw new BizException("单个商品最多支持 " + MAX_SPEC_GROUP_COUNT + " 组销售规格");
        }
        Set<String> seenGroupNames = new HashSet<>();
        for (ProductSpecGroup group : groups) {
            if (group == null || group.getSpecName() == null || group.getSpecName().trim().isEmpty()) {
                throw new BizException("销售规格组名称不能为空");
            }
            String groupName = group.getSpecName().trim();
            if (!seenGroupNames.add(groupName)) {
                throw new BizException("同一商品下不能存在重复的销售规格组：" + groupName);
            }
            group.setSpecName(groupName);
            List<ProductSpecValue> values = group.getValues();
            if (values == null || values.isEmpty()) {
                throw new BizException("销售规格组 [" + groupName + "] 至少需要 1 个规格值");
            }
            if (values.size() > MAX_SPEC_VALUE_PER_GROUP) {
                throw new BizException("销售规格组 [" + groupName + "] 最多支持 " + MAX_SPEC_VALUE_PER_GROUP + " 个规格值");
            }
            Set<String> seenValues = new HashSet<>();
            for (ProductSpecValue value : values) {
                if (value == null || value.getSpecValue() == null || value.getSpecValue().trim().isEmpty()) {
                    throw new BizException("销售规格值不能为空");
                }
                String v = value.getSpecValue().trim();
                if (!seenValues.add(v)) {
                    throw new BizException("规格组 [" + groupName + "] 存在重复规格值：" + v);
                }
                value.setSpecValue(v);
            }
        }
    }

    /**
     * 按 groupId 升序生成规范化规格组合键（g1:v1;g2:v2），保证同组合唯一。
     */
    public static String buildSpecKey(Map<Long, Long> groupIdToValueId) {
        if (groupIdToValueId == null || groupIdToValueId.isEmpty()) {
            return DEFAULT_SPEC_KEY;
        }
        return groupIdToValueId.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + ":" + e.getValue())
            .collect(Collectors.joining(";"));
    }

    /**
     * 按规格组顺序拼接可读规格摘要（如 "标准版 / 永久有效"）。
     */
    public static String buildSpecText(List<ProductSpecGroup> orderedGroups, Map<Long, String> groupIdToValueText) {
        if (orderedGroups == null || orderedGroups.isEmpty() || groupIdToValueText == null || groupIdToValueText.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (ProductSpecGroup group : orderedGroups) {
            String val = groupIdToValueText.get(group.getId());
            if (val != null && !val.isEmpty()) {
                parts.add(val);
            }
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    /**
     * 生成结构化规格 JSON 数组：[{"name":"版本","value":"标准版"}]。
     */
    public static String buildSpecJson(List<ProductSpecGroup> orderedGroups, Map<Long, String> groupIdToValueText) {
        if (orderedGroups == null || orderedGroups.isEmpty() || groupIdToValueText == null || groupIdToValueText.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ProductSpecGroup group : orderedGroups) {
            String val = groupIdToValueText.get(group.getId());
            if (val == null) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"name\":\"").append(escapeJson(group.getSpecName()))
                .append("\",\"value\":\"").append(escapeJson(val)).append("\"}");
        }
        sb.append(']');
        return first ? null : sb.toString();
    }

    /**
     * 计算 SPU 价格区间与默认 SKU（优先从 ENABLED SKU 中选取最低价/最早创建的 SKU）。
     */
    public static PriceSummary computeSummary(List<Product> skus) {
        if (skus == null || skus.isEmpty()) {
            throw new BizException("SPU 至少需要包含一个 SKU");
        }
        List<Product> enabled = skus.stream()
            .filter(s -> CommonStatus.ENABLED.getType().equals(s.getProductStatus()))
            .toList();
        List<Product> target = enabled.isEmpty() ? skus : enabled;
        int minPrice = target.stream().map(Product::getPrice).min(Integer::compareTo).orElse(1);
        int maxPrice = target.stream().map(Product::getPrice).max(Integer::compareTo).orElse(minPrice);
        Long defaultSkuId = target.stream()
            .min(Comparator.comparing(Product::getPrice)
                .thenComparing(s -> s.getId() == null ? Long.MAX_VALUE : s.getId()))
            .map(Product::getId)
            .orElse(null);
        return new PriceSummary(minPrice, maxPrice, defaultSkuId);
    }

    /**
     * 校验 SPU 是否允许上架：至少存在一个 ENABLED 状态的 SKU。
     */
    public static void validateSpuEnableable(List<Product> skus) {
        boolean hasEnabled = skus != null && skus.stream()
            .anyMatch(s -> CommonStatus.ENABLED.getType().equals(s.getProductStatus()));
        if (!hasEnabled) {
            throw new BizException("上架 SPU 前至少需要一个上架状态的 SKU");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record PriceSummary(int minPrice, int maxPrice, Long defaultSkuId) {
    }
}
