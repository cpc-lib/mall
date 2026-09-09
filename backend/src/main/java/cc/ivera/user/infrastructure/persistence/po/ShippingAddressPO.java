package cc.ivera.user.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户收货地址：多地址管理 + 默认地址 + 省市区三级。
 */
@Data
@TableName("t_shipping_address")
public class ShippingAddressPO extends BaseEntity {
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detail;
    /**
     * '0'=非默认 '1'=默认
     */
    private String isDefault;
}
