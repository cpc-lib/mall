package cc.ivera.product.application.impl;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.product.application.ProductService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.PageVO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Service
public class ProductServiceImpl implements ProductService, ProductQueryApi {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public List<Product> list() {
        return productRepository.findAll();
    }

    @Override
    public PageVO<Product> page(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = productRepository.countAll();
        List<Product> records = total > 0
            ? productRepository.findPage(safeSize, (long) (safePage - 1) * safeSize)
            : List.of();
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
        return product;
    }

    @Override
    public Product findById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public List<Product> findByIds(Collection<Long> ids) {
        return productRepository.findByIds(ids);
    }

    @Override
    public Product create(String title, Integer price, Integer stock) {
        if (title == null || title.trim().isEmpty()) throw new BizException("商品名称不能为空");
        if (price == null || price <= 0) throw new BizException("价格必须大于 0");
        if (stock == null || stock < 0) throw new BizException("库存不能为负数");
        Product product = Product.create(title.trim(), price, stock);
        Date now = new Date();
        product.setCreateTime(now);
        product.setUpdateTime(now);
        productRepository.insert(product);
        return product;
    }

    @Override
    public Product changeStatus(Long id, String productStatus) {
        if (!Arrays.asList(CommonStatus.ENABLED.getType(), CommonStatus.DISABLED.getType()).contains(productStatus)) {
            throw new BizException("商品状态仅支持 ENABLED / DISABLED");
        }
        Product product = getById(id);
        product.changeStatus(productStatus, new Date());
        productRepository.update(product);
        return product;
    }
}
