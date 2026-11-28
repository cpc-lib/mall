package cc.ivera.product.infrastructure.persistence.repository;

import cc.ivera.product.domain.model.ProductSpecGroup;
import cc.ivera.product.domain.model.ProductSpecValue;
import cc.ivera.product.domain.model.ProductSpu;
import cc.ivera.product.domain.model.ProductSpuAttr;
import cc.ivera.product.domain.repository.ProductSpuRepository;
import cc.ivera.product.infrastructure.persistence.mapper.ProductSpecGroupMapper;
import cc.ivera.product.infrastructure.persistence.mapper.ProductSpecValueMapper;
import cc.ivera.product.infrastructure.persistence.mapper.ProductSpuAttrMapper;
import cc.ivera.product.infrastructure.persistence.mapper.ProductSpuMapper;
import cc.ivera.product.infrastructure.persistence.po.ProductSpecGroupPO;
import cc.ivera.product.infrastructure.persistence.po.ProductSpecValuePO;
import cc.ivera.product.infrastructure.persistence.po.ProductSpuAttrPO;
import cc.ivera.product.infrastructure.persistence.po.ProductSpuPO;
import cc.ivera.shared.domain.enums.CommonStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class ProductSpuRepositoryImpl implements ProductSpuRepository {

    private final ProductSpuMapper spuMapper;
    private final ProductSpuAttrMapper attrMapper;
    private final ProductSpecGroupMapper specGroupMapper;
    private final ProductSpecValueMapper specValueMapper;

    public ProductSpuRepositoryImpl(ProductSpuMapper spuMapper,
                                    ProductSpuAttrMapper attrMapper,
                                    ProductSpecGroupMapper specGroupMapper,
                                    ProductSpecValueMapper specValueMapper) {
        this.spuMapper = spuMapper;
        this.attrMapper = attrMapper;
        this.specGroupMapper = specGroupMapper;
        this.specValueMapper = specValueMapper;
    }

    @Override
    public ProductSpu findById(Long id) {
        if (id == null) {
            return null;
        }
        return toSpuDomain(spuMapper.selectById(id));
    }

    @Override
    public List<ProductSpu> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return spuMapper.selectBatchIds(ids).stream()
            .map(ProductSpuRepositoryImpl::toSpuDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long countSpus(String keyword, boolean onlyEnabled) {
        Long count = spuMapper.selectCount(buildSpuQuery(keyword, onlyEnabled));
        return count == null ? 0L : count;
    }

    @Override
    public List<ProductSpu> findSpuPage(String keyword, boolean onlyEnabled, int limit, long offset) {
        LambdaQueryWrapper<ProductSpuPO> qw = buildSpuQuery(keyword, onlyEnabled)
            .orderByAsc(ProductSpuPO::getId)
            .last("limit " + limit + " offset " + offset);
        return spuMapper.selectList(qw).stream()
            .map(ProductSpuRepositoryImpl::toSpuDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void insertSpu(ProductSpu spu) {
        ProductSpuPO po = toSpuPO(spu);
        spuMapper.insert(po);
        spu.setId(po.getId());
        spu.setCreateTime(po.getCreateTime());
        spu.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void updateSpu(ProductSpu spu) {
        spuMapper.updateById(toSpuPO(spu));
    }

    @Override
    public List<ProductSpuAttr> listAttrsBySpuId(Long spuId) {
        if (spuId == null) {
            return List.of();
        }
        return attrMapper.selectList(new LambdaQueryWrapper<ProductSpuAttrPO>()
                .eq(ProductSpuAttrPO::getSpuId, spuId)
                .orderByAsc(ProductSpuAttrPO::getSortOrder)
                .orderByAsc(ProductSpuAttrPO::getId))
            .stream()
            .map(ProductSpuRepositoryImpl::toAttrDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<ProductSpuAttr> listAttrsBySpuIds(Collection<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        return attrMapper.selectList(new LambdaQueryWrapper<ProductSpuAttrPO>()
                .in(ProductSpuAttrPO::getSpuId, spuIds)
                .orderByAsc(ProductSpuAttrPO::getSortOrder)
                .orderByAsc(ProductSpuAttrPO::getId))
            .stream()
            .map(ProductSpuRepositoryImpl::toAttrDomain)
            .collect(Collectors.toList());
    }

    @Override
    public void replaceAttrs(Long spuId, List<ProductSpuAttr> attrs) {
        attrMapper.delete(new LambdaQueryWrapper<ProductSpuAttrPO>()
            .eq(ProductSpuAttrPO::getSpuId, spuId));
        if (attrs == null || attrs.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (int i = 0; i < attrs.size(); i++) {
            ProductSpuAttr attr = attrs.get(i);
            ProductSpuAttrPO po = new ProductSpuAttrPO();
            po.setSpuId(spuId);
            po.setAttrName(attr.getAttrName());
            po.setAttrValue(attr.getAttrValue());
            po.setSortOrder(attr.getSortOrder() == null ? i : attr.getSortOrder());
            po.setCreateTime(now);
            po.setUpdateTime(now);
            attrMapper.insert(po);
            attr.setId(po.getId());
            attr.setSpuId(spuId);
        }
    }

    @Override
    public List<ProductSpecGroup> listSpecGroupsWithValues(Long spuId) {
        if (spuId == null) {
            return List.of();
        }
        List<ProductSpecGroupPO> groupPOs = specGroupMapper.selectList(
            new LambdaQueryWrapper<ProductSpecGroupPO>()
                .eq(ProductSpecGroupPO::getSpuId, spuId)
                .orderByAsc(ProductSpecGroupPO::getSortOrder)
                .orderByAsc(ProductSpecGroupPO::getId));
        if (groupPOs.isEmpty()) {
            return List.of();
        }
        List<ProductSpecValuePO> valuePOs = specValueMapper.selectList(
            new LambdaQueryWrapper<ProductSpecValuePO>()
                .eq(ProductSpecValuePO::getSpuId, spuId)
                .orderByAsc(ProductSpecValuePO::getSortOrder)
                .orderByAsc(ProductSpecValuePO::getId));
        Map<Long, List<ProductSpecValue>> valuesByGroup = new HashMap<>();
        for (ProductSpecValuePO vpo : valuePOs) {
            valuesByGroup.computeIfAbsent(vpo.getGroupId(), k -> new ArrayList<>())
                .add(toSpecValueDomain(vpo));
        }
        List<ProductSpecGroup> result = new ArrayList<>(groupPOs.size());
        for (ProductSpecGroupPO gpo : groupPOs) {
            ProductSpecGroup group = toSpecGroupDomain(gpo);
            group.setValues(valuesByGroup.getOrDefault(gpo.getId(), new ArrayList<>()));
            result.add(group);
        }
        return result;
    }

    @Override
    public List<ProductSpecGroup> syncSpecGroupsAndValues(Long spuId, List<ProductSpecGroup> desiredGroups) {
        List<ProductSpecGroupPO> existingGroups = specGroupMapper.selectList(
            new LambdaQueryWrapper<ProductSpecGroupPO>()
                .eq(ProductSpecGroupPO::getSpuId, spuId));
        if (desiredGroups == null || desiredGroups.isEmpty()) {
            if (!existingGroups.isEmpty()) {
                specValueMapper.delete(new LambdaQueryWrapper<ProductSpecValuePO>()
                    .eq(ProductSpecValuePO::getSpuId, spuId));
                specGroupMapper.delete(new LambdaQueryWrapper<ProductSpecGroupPO>()
                    .eq(ProductSpecGroupPO::getSpuId, spuId));
            }
            return List.of();
        }

        Map<String, ProductSpecGroupPO> existingGroupByName = new HashMap<>();
        for (ProductSpecGroupPO gpo : existingGroups) {
            existingGroupByName.put(gpo.getSpecName(), gpo);
        }
        List<ProductSpecValuePO> existingValues = specValueMapper.selectList(
            new LambdaQueryWrapper<ProductSpecValuePO>()
                .eq(ProductSpecValuePO::getSpuId, spuId));
        Map<Long, Map<String, ProductSpecValuePO>> existingValueByGroupAndText = new HashMap<>();
        for (ProductSpecValuePO vpo : existingValues) {
            existingValueByGroupAndText
                .computeIfAbsent(vpo.getGroupId(), k -> new HashMap<>())
                .put(vpo.getSpecValue(), vpo);
        }

        Date now = new Date();
        Set<Long> retainedGroupIds = new HashSet<>();
        Set<Long> retainedValueIds = new HashSet<>();
        List<ProductSpecGroup> synced = new ArrayList<>(desiredGroups.size());

        for (int gi = 0; gi < desiredGroups.size(); gi++) {
            ProductSpecGroup desiredGroup = desiredGroups.get(gi);
            int groupSort = desiredGroup.getSortOrder() == null ? gi : desiredGroup.getSortOrder();
            ProductSpecGroupPO gpo = existingGroupByName.get(desiredGroup.getSpecName());
            if (gpo == null) {
                gpo = new ProductSpecGroupPO();
                gpo.setSpuId(spuId);
                gpo.setSpecName(desiredGroup.getSpecName());
                gpo.setSortOrder(groupSort);
                gpo.setCreateTime(now);
                gpo.setUpdateTime(now);
                specGroupMapper.insert(gpo);
            } else {
                gpo.setSortOrder(groupSort);
                gpo.setUpdateTime(now);
                specGroupMapper.updateById(gpo);
            }
            retainedGroupIds.add(gpo.getId());

            Map<String, ProductSpecValuePO> existingVals =
                existingValueByGroupAndText.getOrDefault(gpo.getId(), Map.of());
            List<ProductSpecValue> syncedValues = new ArrayList<>();
            List<ProductSpecValue> desiredValues = desiredGroup.getValues() == null
                ? List.of() : desiredGroup.getValues();
            for (int vi = 0; vi < desiredValues.size(); vi++) {
                ProductSpecValue dv = desiredValues.get(vi);
                int valSort = dv.getSortOrder() == null ? vi : dv.getSortOrder();
                ProductSpecValuePO vpo = existingVals.get(dv.getSpecValue());
                if (vpo == null) {
                    vpo = new ProductSpecValuePO();
                    vpo.setSpuId(spuId);
                    vpo.setGroupId(gpo.getId());
                    vpo.setSpecValue(dv.getSpecValue());
                    vpo.setSortOrder(valSort);
                    vpo.setCreateTime(now);
                    vpo.setUpdateTime(now);
                    specValueMapper.insert(vpo);
                } else {
                    vpo.setSortOrder(valSort);
                    vpo.setUpdateTime(now);
                    specValueMapper.updateById(vpo);
                }
                retainedValueIds.add(vpo.getId());
                syncedValues.add(toSpecValueDomain(vpo));
            }

            ProductSpecGroup syncedGroup = toSpecGroupDomain(gpo);
            syncedGroup.setValues(syncedValues);
            synced.add(syncedGroup);
        }

        // 清理本次编辑不再保留的旧规格值与规格组
        List<Long> obsoleteValueIds = existingValues.stream()
            .map(ProductSpecValuePO::getId)
            .filter(id -> !retainedValueIds.contains(id))
            .toList();
        if (!obsoleteValueIds.isEmpty()) {
            specValueMapper.deleteBatchIds(obsoleteValueIds);
        }
        List<Long> obsoleteGroupIds = existingGroups.stream()
            .map(ProductSpecGroupPO::getId)
            .filter(id -> !retainedGroupIds.contains(id))
            .toList();
        if (!obsoleteGroupIds.isEmpty()) {
            specGroupMapper.deleteBatchIds(obsoleteGroupIds);
        }

        return synced;
    }

    private LambdaQueryWrapper<ProductSpuPO> buildSpuQuery(String keyword, boolean onlyEnabled) {
        LambdaQueryWrapper<ProductSpuPO> qw = new LambdaQueryWrapper<>();
        if (onlyEnabled) {
            qw.eq(ProductSpuPO::getSpuStatus, CommonStatus.ENABLED.getType());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            qw.and(q -> q.like(ProductSpuPO::getTitle, kw)
                .or().like(ProductSpuPO::getSubtitle, kw)
                .or().like(ProductSpuPO::getSpuCode, kw)
                .or().like(ProductSpuPO::getBrandName, kw)
                .or().like(ProductSpuPO::getCategoryName, kw));
        }
        return qw;
    }

    private static ProductSpuPO toSpuPO(ProductSpu d) {
        if (d == null) return null;
        ProductSpuPO po = new ProductSpuPO();
        po.setId(d.getId());
        po.setSpuCode(d.getSpuCode());
        po.setTitle(d.getTitle());
        po.setSubtitle(d.getSubtitle());
        po.setCategoryName(d.getCategoryName());
        po.setBrandName(d.getBrandName());
        po.setMainImage(d.getMainImage());
        po.setDescription(d.getDescription());
        po.setSpecMode(d.getSpecMode());
        po.setSpuStatus(d.getSpuStatus());
        po.setMinPrice(d.getMinPrice());
        po.setMaxPrice(d.getMaxPrice());
        po.setDefaultSkuId(d.getDefaultSkuId());
        po.setCreateTime(d.getCreateTime());
        po.setUpdateTime(d.getUpdateTime());
        return po;
    }

    private static ProductSpu toSpuDomain(ProductSpuPO po) {
        if (po == null) return null;
        ProductSpu d = new ProductSpu();
        d.setId(po.getId());
        d.setSpuCode(po.getSpuCode());
        d.setTitle(po.getTitle());
        d.setSubtitle(po.getSubtitle());
        d.setCategoryName(po.getCategoryName());
        d.setBrandName(po.getBrandName());
        d.setMainImage(po.getMainImage());
        d.setDescription(po.getDescription());
        d.setSpecMode(po.getSpecMode());
        d.setSpuStatus(po.getSpuStatus());
        d.setMinPrice(po.getMinPrice());
        d.setMaxPrice(po.getMaxPrice());
        d.setDefaultSkuId(po.getDefaultSkuId());
        d.setCreateTime(po.getCreateTime());
        d.setUpdateTime(po.getUpdateTime());
        return d;
    }

    private static ProductSpuAttr toAttrDomain(ProductSpuAttrPO po) {
        ProductSpuAttr attr = new ProductSpuAttr();
        attr.setId(po.getId());
        attr.setSpuId(po.getSpuId());
        attr.setAttrName(po.getAttrName());
        attr.setAttrValue(po.getAttrValue());
        attr.setSortOrder(po.getSortOrder());
        return attr;
    }

    private static ProductSpecGroup toSpecGroupDomain(ProductSpecGroupPO po) {
        ProductSpecGroup group = new ProductSpecGroup();
        group.setId(po.getId());
        group.setSpuId(po.getSpuId());
        group.setSpecName(po.getSpecName());
        group.setSortOrder(po.getSortOrder());
        return group;
    }

    private static ProductSpecValue toSpecValueDomain(ProductSpecValuePO po) {
        ProductSpecValue value = new ProductSpecValue();
        value.setId(po.getId());
        value.setSpuId(po.getSpuId());
        value.setGroupId(po.getGroupId());
        value.setSpecValue(po.getSpecValue());
        value.setSortOrder(po.getSortOrder());
        return value;
    }
}
