package cc.ivera.product.infrastructure.persistence.repository;

import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.product.infrastructure.persistence.converter.ProductPOConverter;
import cc.ivera.product.infrastructure.persistence.mapper.ProductMapper;
import cc.ivera.product.infrastructure.persistence.po.ProductPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductMapper productMapper;

    public ProductRepositoryImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public Product findById(Long id) {
        return ProductPOConverter.toDomain(productMapper.selectById(id));
    }

    @Override
    public List<Product> findAll() {
        List<ProductPO> rows = productMapper.selectList(
            new LambdaQueryWrapper<ProductPO>().orderByAsc(ProductPO::getId));
        return rows.stream().map(ProductPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public void insert(Product product) {
        ProductPO po = ProductPOConverter.toPO(product);
        productMapper.insert(po);
        product.setId(po.getId());
        product.setCreateTime(po.getCreateTime());
        product.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(Product product) {
        productMapper.updateById(ProductPOConverter.toPO(product));
    }

    @Override
    public boolean adjustAvailable(Long id, int delta) {
        LambdaUpdateWrapper<ProductPO> uw = new LambdaUpdateWrapper<>();
        uw.eq(ProductPO::getId, id).setSql("available_stock = available_stock + " + delta).apply("available_stock + {0} >= 0", delta);
        return productMapper.update(null, uw) > 0;
    }

    @Override
    public int reserveStock(Long id, int quantity) {
        return productMapper.reserveStock(id, quantity);
    }

    @Override
    public int releaseReservedStock(Long id, int quantity) {
        return productMapper.releaseReservedStock(id, quantity);
    }

    @Override
    public int commitSoldStock(Long id, int quantity) {
        return productMapper.commitSoldStock(id, quantity);
    }

    @Override
    public int releaseSoldStock(Long id, int quantity) {
        return productMapper.releaseSoldStock(id, quantity);
    }

    @Override
    public int writeOffLostStock(Long id, int quantity) {
        return productMapper.writeOffLostStock(id, quantity);
    }

    @Override
    public int writeOffSoldLostStock(Long id, int quantity) {
        return productMapper.writeOffSoldLostStock(id, quantity);
    }
}
