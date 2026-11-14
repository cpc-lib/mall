package cc.ivera.cart.infrastructure.persistence.converter;

import cc.ivera.cart.domain.model.CartItem;
import cc.ivera.cart.infrastructure.persistence.po.CartItemPO;

/**
 * CartItem ↔ CartItemPO 转换；selected 为空时按已勾选处理，与历史实现保持一致。
 */
public final class CartItemPOConverter {

    private CartItemPOConverter() {
    }

    public static CartItemPO toPO(Long userId, CartItem item) {
        if (item == null) {
            return null;
        }
        CartItemPO po = new CartItemPO();
        po.setUserId(userId);
        po.setProductId(item.getProductId());
        po.setQuantity(item.getQuantity());
        po.setSelected(Boolean.FALSE.equals(item.getSelected()) ? "0" : "1");
        return po;
    }

    public static CartItem toDomain(CartItemPO po) {
        if (po == null) {
            return null;
        }
        CartItem item = new CartItem();
        item.setProductId(po.getProductId());
        item.setQuantity(po.getQuantity());
        item.setSelected(!"0".equals(po.getSelected()));
        return item;
    }
}
