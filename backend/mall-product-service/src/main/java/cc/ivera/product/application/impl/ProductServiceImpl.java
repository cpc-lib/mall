package cc.ivera.product.application.impl;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.product.application.ProductService;
import cc.ivera.product.application.dto.SpuSaveRequest;
import cc.ivera.product.application.vo.SpuDetailVO;
import cc.ivera.product.application.vo.SpuSummaryVO;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ProductSpecGroup;
import cc.ivera.product.domain.model.ProductSpecValue;
import cc.ivera.product.domain.model.ProductSpu;
import cc.ivera.product.domain.model.ProductSpuAttr;
import cc.ivera.product.domain.policy.SpuSpecPolicy;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.product.domain.repository.ProductSpuRepository;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService, ProductQueryApi {

    private final ProductRepository productRepository;
    private final ProductSpuRepository productSpuRepository;

    public ProductServiceImpl(ProductRepository productRepository, ProductSpuRepository productSpuRepository) {
        this.productRepository = productRepository;
        this.productSpuRepository = productSpuRepository;
    }

    @Override
    public PageVO<Product> page(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = productRepository.countAll();
        List<Product> records = total > 0
            ? productRepository.findPage(safeSize, (long) (safePage - 1) * safeSize)
            : List.of();
        enrichSpuStatus(records);
        PageVO<Product> result = new PageVO<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(records);
        return result;
    }

    @Override
    public Product getById(Long id) {
        Product product = productRepository.findById(id);
        if (product == null) throw new BizException("商品不存在");
        enrichSpuStatus(product);
        return product;
    }

    @Override
    public Product findById(Long id) {
        Product product = productRepository.findById(id);
        enrichSpuStatus(product);
        return product;
    }

    @Override
    public List<Product> findByIds(Collection<Long> ids) {
        List<Product> products = productRepository.findByIds(ids);
        enrichSpuStatus(products);
        return products;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product create(String title, Integer price, Integer stock) {
        if (title == null || title.trim().isEmpty()) throw new BizException("商品名称不能为空");
        if (price == null || price <= 0) throw new BizException("价格必须大于 0");
        if (stock == null || stock < 0) throw new BizException("库存不能为负数");

        Date now = new Date();
        String trimmedTitle = title.trim();

        ProductSpu spu = new ProductSpu();
        spu.setTitle(trimmedTitle);
        spu.setSpecMode(ProductSpu.SPEC_MODE_SINGLE);
        spu.setSpuStatus(CommonStatus.ENABLED.getType());
        spu.setMinPrice(price);
        spu.setMaxPrice(price);
        spu.setCreateTime(now);
        spu.setUpdateTime(now);
        productSpuRepository.insertSpu(spu);

        Product product = Product.create(trimmedTitle, price, stock);
        product.setSpuId(spu.getId());
        product.setSpuStatus(spu.getSpuStatus());
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productRepository.insert(product);

        spu.setDefaultSkuId(product.getId());
        spu.setUpdateTime(now);
        productSpuRepository.updateSpu(spu);

        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product changeStatus(Long id, String productStatus) {
        validateStatus(productStatus);
        Product product = getById(id);
        Date now = new Date();
        product.changeStatus(productStatus, now);
        productRepository.update(product);

        if (product.getSpuId() != null) {
            ProductSpu spu = productSpuRepository.findById(product.getSpuId());
            if (spu != null) {
                refreshSpuPriceSummary(spu, now);
                product.setSpuStatus(spu.getSpuStatus());
            }
        }
        return product;
    }

    @Override
    public PageVO<SpuSummaryVO> pageSpu(int page, int size, String keyword, boolean onlyEnabled) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = productSpuRepository.countSpus(keyword, onlyEnabled);
        List<ProductSpu> spus = total > 0
            ? productSpuRepository.findSpuPage(keyword, onlyEnabled, safeSize, (long) (safePage - 1) * safeSize)
            : List.of();

        List<Long> spuIds = spus.stream().map(ProductSpu::getId).toList();
        Map<Long, List<Product>> skusBySpu = productRepository.findBySpuIds(spuIds).stream()
            .filter(p -> p.getSpuId() != null)
            .collect(Collectors.groupingBy(Product::getSpuId));
        Map<Long, List<ProductSpuAttr>> attrsBySpu = productSpuRepository.listAttrsBySpuIds(spuIds).stream()
            .collect(Collectors.groupingBy(ProductSpuAttr::getSpuId));

        List<SpuSummaryVO> records = new ArrayList<>(spus.size());
        for (ProductSpu spu : spus) {
            List<Product> skus = skusBySpu.getOrDefault(spu.getId(), List.of());
            List<ProductSpuAttr> attrs = attrsBySpu.getOrDefault(spu.getId(), List.of());
            records.add(buildSpuSummary(spu, skus, attrs));
        }

        PageVO<SpuSummaryVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(records);
        return result;
    }

    @Override
    public SpuDetailVO getSpuDetail(Long spuId) {
        ProductSpu spu = productSpuRepository.findById(spuId);
        if (spu == null) {
            throw new BizException("SPU 商品不存在");
        }
        List<ProductSpuAttr> attrs = productSpuRepository.listAttrsBySpuId(spuId);
        List<ProductSpecGroup> specGroups = productSpuRepository.listSpecGroupsWithValues(spuId);
        List<Product> skus = productRepository.findBySpuId(spuId);
        for (Product sku : skus) {
            sku.setSpuStatus(spu.getSpuStatus());
        }
        SpuDetailVO vo = new SpuDetailVO();
        vo.setSpu(spu);
        vo.setAttrs(attrs);
        vo.setSpecGroups(specGroups);
        vo.setSkus(skus);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuDetailVO createSpu(SpuSaveRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        String specMode = SpuSpecPolicy.normalizeSpecMode(request.getSpecMode());
        List<ProductSpuAttr> attrs = toAttrModels(request.getAttrs());
        SpuSpecPolicy.validateAttributes(attrs);

        List<ProductSpecGroup> desiredGroups = toSpecGroupModels(request.getSpecGroups());
        SpuSpecPolicy.validateSpecGroups(specMode, desiredGroups);

        List<SpuSaveRequest.SkuItem> skuItems = request.getSkus();
        if (skuItems == null || skuItems.isEmpty()) {
            throw new BizException("至少需要配置一个 SKU");
        }
        if (ProductSpu.SPEC_MODE_SINGLE.equals(specMode) && skuItems.size() != 1) {
            throw new BizException("单规格商品必须且只能配置 1 个 SKU");
        }

        Date now = new Date();
        boolean anyEnabled = skuItems.stream()
            .anyMatch(s -> CommonStatus.ENABLED.getType().equals(normalizeSkuStatus(s.getProductStatus())));

        ProductSpu spu = new ProductSpu();
        applySpuBaseFields(spu, request, specMode);
        spu.setSpuStatus(anyEnabled ? CommonStatus.ENABLED.getType() : CommonStatus.DISABLED.getType());
        spu.setMinPrice(skuItems.get(0).getPrice());
        spu.setMaxPrice(skuItems.get(0).getPrice());
        spu.setCreateTime(now);
        spu.setUpdateTime(now);
        productSpuRepository.insertSpu(spu);

        productSpuRepository.replaceAttrs(spu.getId(), attrs);
        List<ProductSpecGroup> syncedGroups = ProductSpu.SPEC_MODE_MULTI.equals(specMode)
            ? productSpuRepository.syncSpecGroupsAndValues(spu.getId(), desiredGroups)
            : List.of();

        Set<String> seenSpecKeys = new HashSet<>();
        for (SpuSaveRequest.SkuItem item : skuItems) {
            ResolvedSpec resolved = resolveSkuSpec(specMode, syncedGroups, item.getSpecs());
            if (!seenSpecKeys.add(resolved.specKey())) {
                throw new BizException("存在重复的规格组合：" + (resolved.specText() == null ? resolved.specKey() : resolved.specText()));
            }
            Product sku = Product.create(spu.getTitle(), item.getPrice(), item.getStock() == null ? 0 : item.getStock());
            sku.setSpuId(spu.getId());
            sku.setSpuStatus(spu.getSpuStatus());
            sku.setSkuCode(trimToNull(item.getSkuCode()));
            sku.setSpecKey(resolved.specKey());
            sku.setSpecText(resolved.specText());
            sku.setSpecJson(resolved.specJson());
            sku.setIsDefault(false);
            sku.setProductStatus(normalizeSkuStatus(item.getProductStatus()));
            sku.setCreateTime(now);
            sku.setUpdateTime(now);
            productRepository.insert(sku);
        }

        refreshSpuPriceSummary(spu, now);
        return getSpuDetail(spu.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuDetailVO updateSpu(Long spuId, SpuSaveRequest request) {
        if (request == null) {
            throw new BizException("请求体不能为空");
        }
        ProductSpu spu = productSpuRepository.findById(spuId);
        if (spu == null) {
            throw new BizException("SPU 商品不存在");
        }

        String specMode = SpuSpecPolicy.normalizeSpecMode(request.getSpecMode());
        List<ProductSpuAttr> attrs = toAttrModels(request.getAttrs());
        SpuSpecPolicy.validateAttributes(attrs);

        List<ProductSpecGroup> desiredGroups = toSpecGroupModels(request.getSpecGroups());
        SpuSpecPolicy.validateSpecGroups(specMode, desiredGroups);

        List<SpuSaveRequest.SkuItem> skuItems = request.getSkus();
        if (skuItems == null || skuItems.isEmpty()) {
            throw new BizException("至少需要配置一个 SKU");
        }
        if (ProductSpu.SPEC_MODE_SINGLE.equals(specMode) && skuItems.size() != 1) {
            throw new BizException("单规格商品必须且只能配置 1 个 SKU");
        }

        Date now = new Date();
        applySpuBaseFields(spu, request, specMode);
        spu.setUpdateTime(now);
        productSpuRepository.updateSpu(spu);

        productSpuRepository.replaceAttrs(spuId, attrs);
        List<ProductSpecGroup> syncedGroups = ProductSpu.SPEC_MODE_MULTI.equals(specMode)
            ? productSpuRepository.syncSpecGroupsAndValues(spuId, desiredGroups)
            : productSpuRepository.syncSpecGroupsAndValues(spuId, List.of());

        List<Product> existingSkus = productRepository.findBySpuId(spuId);
        Map<Long, Product> existingById = new HashMap<>();
        Map<String, Product> existingBySpecKey = new HashMap<>();
        for (Product existing : existingSkus) {
            existingById.put(existing.getId(), existing);
            if (existing.getSpecKey() != null) {
                existingBySpecKey.put(existing.getSpecKey(), existing);
            }
        }

        Set<Long> touchedSkuIds = new HashSet<>();
        Set<String> seenSpecKeys = new HashSet<>();

        for (SpuSaveRequest.SkuItem item : skuItems) {
            ResolvedSpec resolved = resolveSkuSpec(specMode, syncedGroups, item.getSpecs());
            if (!seenSpecKeys.add(resolved.specKey())) {
                throw new BizException("存在重复的规格组合：" + (resolved.specText() == null ? resolved.specKey() : resolved.specText()));
            }
            String skuStatus = normalizeSkuStatus(item.getProductStatus());

            Product target = null;
            if (item.getSkuId() != null) {
                target = existingById.get(item.getSkuId());
                if (target == null) {
                    throw new BizException("SKU 不属于当前 SPU，skuId=" + item.getSkuId());
                }
            } else {
                target = existingBySpecKey.get(resolved.specKey());
            }

            if (target != null) {
                // 已有 SKU 只更新元数据、价格与状态，绝不覆盖库存四桶
                target.setTitle(spu.getTitle());
                target.setSkuCode(trimToNull(item.getSkuCode()));
                target.setSpecKey(resolved.specKey());
                target.setSpecText(resolved.specText());
                target.setSpecJson(resolved.specJson());
                target.setPrice(item.getPrice());
                target.setProductStatus(skuStatus);
                target.setUpdateTime(now);
                productRepository.update(target);
                touchedSkuIds.add(target.getId());
            } else {
                Product sku = Product.create(spu.getTitle(), item.getPrice(), item.getStock() == null ? 0 : item.getStock());
                sku.setSpuId(spuId);
                sku.setSpuStatus(spu.getSpuStatus());
                sku.setSkuCode(trimToNull(item.getSkuCode()));
                sku.setSpecKey(resolved.specKey());
                sku.setSpecText(resolved.specText());
                sku.setSpecJson(resolved.specJson());
                sku.setIsDefault(false);
                sku.setProductStatus(skuStatus);
                sku.setCreateTime(now);
                sku.setUpdateTime(now);
                productRepository.insert(sku);
                touchedSkuIds.add(sku.getId());
            }
        }

        // 本次未提交的已有 SKU 仅置为 DISABLED，绝不物理删除，保护四桶库存与历史订单引用
        for (Product existing : existingSkus) {
            if (!touchedSkuIds.contains(existing.getId())) {
                if (!CommonStatus.DISABLED.getType().equals(existing.getProductStatus())
                    || Boolean.TRUE.equals(existing.getIsDefault())) {
                    existing.setProductStatus(CommonStatus.DISABLED.getType());
                    existing.setIsDefault(false);
                    existing.setUpdateTime(now);
                    productRepository.update(existing);
                }
            }
        }

        refreshSpuPriceSummary(spu, now);
        return getSpuDetail(spuId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuDetailVO changeSpuStatus(Long spuId, String spuStatus) {
        validateStatus(spuStatus);
        ProductSpu spu = productSpuRepository.findById(spuId);
        if (spu == null) {
            throw new BizException("SPU 商品不存在");
        }
        if (CommonStatus.ENABLED.getType().equals(spuStatus)) {
            List<Product> skus = productRepository.findBySpuId(spuId);
            SpuSpecPolicy.validateSpuEnableable(skus);
        }
        spu.setSpuStatus(spuStatus);
        spu.setUpdateTime(new Date());
        productSpuRepository.updateSpu(spu);
        return getSpuDetail(spuId);
    }

    private void refreshSpuPriceSummary(ProductSpu spu, Date now) {
        List<Product> allSkus = productRepository.findBySpuId(spu.getId());
        if (allSkus.isEmpty()) {
            return;
        }
        SpuSpecPolicy.PriceSummary summary = SpuSpecPolicy.computeSummary(allSkus);
        spu.setMinPrice(summary.minPrice());
        spu.setMaxPrice(summary.maxPrice());
        spu.setDefaultSkuId(summary.defaultSkuId());

        boolean hasEnabled = allSkus.stream()
            .anyMatch(s -> CommonStatus.ENABLED.getType().equals(s.getProductStatus()));
        if (!hasEnabled && CommonStatus.ENABLED.getType().equals(spu.getSpuStatus())) {
            spu.setSpuStatus(CommonStatus.DISABLED.getType());
        }
        spu.setUpdateTime(now);
        productSpuRepository.updateSpu(spu);

        for (Product sku : allSkus) {
            boolean shouldBeDefault = Objects.equals(sku.getId(), summary.defaultSkuId());
            if (!Objects.equals(Boolean.TRUE.equals(sku.getIsDefault()), shouldBeDefault)) {
                sku.setIsDefault(shouldBeDefault);
                sku.setUpdateTime(now);
                productRepository.update(sku);
            }
        }
    }

    private void enrichSpuStatus(Product product) {
        if (product == null || product.getSpuId() == null) {
            return;
        }
        ProductSpu spu = productSpuRepository.findById(product.getSpuId());
        if (spu != null) {
            product.setSpuStatus(spu.getSpuStatus());
        }
    }

    private void enrichSpuStatus(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        Set<Long> spuIds = products.stream()
            .map(Product::getSpuId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (spuIds.isEmpty()) {
            return;
        }
        Map<Long, String> statusBySpuId = productSpuRepository.findByIds(spuIds).stream()
            .collect(Collectors.toMap(ProductSpu::getId, ProductSpu::getSpuStatus, (a, b) -> a));
        for (Product product : products) {
            if (product.getSpuId() != null) {
                product.setSpuStatus(statusBySpuId.get(product.getSpuId()));
            }
        }
    }

    private SpuSummaryVO buildSpuSummary(ProductSpu spu, List<Product> skus, List<ProductSpuAttr> attrs) {
        SpuSummaryVO vo = new SpuSummaryVO();
        vo.setSpuId(spu.getId());
        vo.setSpuCode(spu.getSpuCode());
        vo.setTitle(spu.getTitle());
        vo.setSubtitle(spu.getSubtitle());
        vo.setCategoryName(spu.getCategoryName());
        vo.setBrandName(spu.getBrandName());
        vo.setMainImage(spu.getMainImage());
        vo.setSpecMode(spu.getSpecMode());
        vo.setSpuStatus(spu.getSpuStatus());
        vo.setMinPrice(spu.getMinPrice());
        vo.setMaxPrice(spu.getMaxPrice());
        vo.setDefaultSkuId(spu.getDefaultSkuId());
        vo.setSkuCount(skus.size());
        vo.setTotalAvailableStock(skus.stream().mapToInt(s -> safeInt(s.getStock())).sum());
        vo.setTotalLockedStock(skus.stream().mapToInt(s -> safeInt(s.getLockedStock())).sum());
        vo.setTotalSoldStock(skus.stream().mapToInt(s -> safeInt(s.getSoldStock())).sum());
        vo.setTotalLostStock(skus.stream().mapToInt(s -> safeInt(s.getLostStock())).sum());
        vo.setAttrSummary(attrs.stream()
            .limit(4)
            .map(a -> a.getAttrName() + ": " + a.getAttrValue())
            .toList());
        vo.setCreateTime(spu.getCreateTime());
        vo.setUpdateTime(spu.getUpdateTime());
        return vo;
    }

    private ResolvedSpec resolveSkuSpec(String specMode,
                                        List<ProductSpecGroup> syncedGroups,
                                        List<SpuSaveRequest.SpecPair> specs) {
        if (ProductSpu.SPEC_MODE_SINGLE.equals(specMode)) {
            return new ResolvedSpec(SpuSpecPolicy.DEFAULT_SPEC_KEY, null, null);
        }
        if (specs == null || specs.size() != syncedGroups.size()) {
            throw new BizException("多规格 SKU 必须为每个销售规格组选择一个规格值");
        }
        Map<String, String> pairByGroupName = new HashMap<>();
        for (SpuSaveRequest.SpecPair pair : specs) {
            if (pair == null || pair.getSpecName() == null || pair.getSpecValue() == null) {
                throw new BizException("SKU 规格键值对不能为空");
            }
            String gName = pair.getSpecName().trim();
            String vText = pair.getSpecValue().trim();
            if (pairByGroupName.put(gName, vText) != null) {
                throw new BizException("SKU 包含重复的规格维度：" + gName);
            }
        }

        Map<Long, Long> groupIdToValueId = new HashMap<>();
        Map<Long, String> groupIdToValueText = new LinkedHashMap<>();
        for (ProductSpecGroup group : syncedGroups) {
            String selectedText = pairByGroupName.get(group.getSpecName());
            if (selectedText == null) {
                throw new BizException("SKU 缺少规格组 [" + group.getSpecName() + "] 的取值");
            }
            ProductSpecValue matchedVal = group.getValues().stream()
                .filter(v -> selectedText.equals(v.getSpecValue()))
                .findFirst()
                .orElseThrow(() -> new BizException("规格组 [" + group.getSpecName() + "] 不存在规格值：" + selectedText));
            groupIdToValueId.put(group.getId(), matchedVal.getId());
            groupIdToValueText.put(group.getId(), matchedVal.getSpecValue());
        }

        String specKey = SpuSpecPolicy.buildSpecKey(groupIdToValueId);
        String specText = SpuSpecPolicy.buildSpecText(syncedGroups, groupIdToValueText);
        String specJson = SpuSpecPolicy.buildSpecJson(syncedGroups, groupIdToValueText);
        return new ResolvedSpec(specKey, specText, specJson);
    }

    private void applySpuBaseFields(ProductSpu spu, SpuSaveRequest request, String specMode) {
        spu.setSpuCode(trimToNull(request.getSpuCode()));
        spu.setTitle(request.getTitle().trim());
        spu.setSubtitle(trimToNull(request.getSubtitle()));
        spu.setCategoryName(trimToNull(request.getCategoryName()));
        spu.setBrandName(trimToNull(request.getBrandName()));
        spu.setMainImage(trimToNull(request.getMainImage()));
        spu.setDescription(trimToNull(request.getDescription()));
        spu.setSpecMode(specMode);
    }

    private List<ProductSpuAttr> toAttrModels(List<SpuSaveRequest.AttrItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ProductSpuAttr> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            SpuSaveRequest.AttrItem item = items.get(i);
            ProductSpuAttr attr = new ProductSpuAttr();
            attr.setAttrName(item.getAttrName());
            attr.setAttrValue(item.getAttrValue());
            attr.setSortOrder(item.getSortOrder() == null ? i : item.getSortOrder());
            result.add(attr);
        }
        return result;
    }

    private List<ProductSpecGroup> toSpecGroupModels(List<SpuSaveRequest.SpecGroupItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<ProductSpecGroup> result = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            SpuSaveRequest.SpecGroupItem item = items.get(i);
            ProductSpecGroup group = new ProductSpecGroup();
            group.setSpecName(item.getSpecName());
            group.setSortOrder(item.getSortOrder() == null ? i : item.getSortOrder());
            List<ProductSpecValue> vals = new ArrayList<>();
            if (item.getValues() != null) {
                for (int vi = 0; vi < item.getValues().size(); vi++) {
                    ProductSpecValue v = new ProductSpecValue();
                    v.setSpecValue(item.getValues().get(vi));
                    v.setSortOrder(vi);
                    vals.add(v);
                }
            }
            group.setValues(vals);
            result.add(group);
        }
        return result;
    }

    private static void validateStatus(String status) {
        if (!Arrays.asList(CommonStatus.ENABLED.getType(), CommonStatus.DISABLED.getType()).contains(status)) {
            throw new BizException("商品状态仅支持 ENABLED / DISABLED");
        }
    }

    private static String normalizeSkuStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return CommonStatus.ENABLED.getType();
        }
        String normalized = status.trim().toUpperCase();
        validateStatus(normalized);
        return normalized;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private record ResolvedSpec(String specKey, String specText, String specJson) {
    }
}
