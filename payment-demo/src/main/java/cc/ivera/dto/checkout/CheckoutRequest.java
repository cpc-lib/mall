package cc.ivera.dto.checkout;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class CheckoutRequest {
    @NotBlank private String paymentType;
    private Long paymentAppId;
    /** 收货人姓名（物流模拟，缺省用模拟值） */
    private String receiverName;
    /** 收货人电话（物流模拟，缺省用模拟值） */
    private String receiverPhone;
    /** 收货地址（物流模拟，缺省用模拟值） */
    private String receiverAddress;
}
