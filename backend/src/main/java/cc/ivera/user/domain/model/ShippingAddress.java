package cc.ivera.user.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 用户收货地址：多地址管理 + 默认地址 + 省市区三级。
 */
@Data
public class ShippingAddress {
    private Long id;
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
    private Date createTime;
    private Date updateTime;
}
