package cc.ivera.service.impl;

import cc.ivera.entity.Product;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

}
