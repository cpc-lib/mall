package cc.ivera.cart.infrastructure.remote;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.cart.infrastructure.remote.client.ProductQueryFeignClient;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

/**
 * 商品查询远程适配器：购物车只读取商品快照（标题/价格/库存/状态），不访问商品库。
 */
@Component
public class ProductQueryRemoteAdapter implements ProductQueryApi {

    private final ProductQueryFeignClient client;

    public ProductQueryRemoteAdapter(ProductQueryFeignClient client) {
        this.client = client;
    }

    @Override
    public Product findById(Long id) {
        return unwrap(client.findById(id));
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("商品服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "商品服务调用失败" : message);
    }
}
