package cc.ivera.order.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.util.Date;

/**
 * 订单 PO：t_order_info（V2 四维状态 + V1 legacy 审计状态）。
 */
@Data
@TableName("t_order_info")
public class OrderInfoPO extends BaseEntity {

    private String title;//订单标题

    private String orderNo;//商户订单编号

    private Long userId;//用户id

    private Long productId;//支付产品id

    private Integer totalFee;//订单金额(分)

    private String codeUrl;//订单二维码连接

    private String legacyStatus;//V1 旧单一状态值（NOTPAY/SUCCESS/...），仅供审计/回滚对照

    private String orderStatus;//交易状态：WAIT_PAY/ACTIVE/CLOSED/COMPLETED

    private String payStatus;//支付状态：UNPAID/PAID

    private String fulfillmentStatus;//履约状态：WAIT_SHIP/SHIPPED/RECEIVED/CANCELLED

    private String refundStatus;//退款汇总状态：NONE/REFUNDING/PARTIAL_REFUNDED/FULL_REFUNDED

    private Integer paidAmount;//有效实付金额(分)

    private Integer refundFrozenAmount;//退款申请冻结金额(分)

    private Integer refundedAmount;//已成功退款金额(分)

    private Date expireTime;//订单过期时间（超时关单/库存预占释放边界）

    private Date paidTime;//支付成功时间

    private String receiverName;//收货人姓名（物流模拟）

    private String receiverPhone;//收货人电话（物流模拟）

    private String receiverAddress;//收货地址（物流模拟）

    private String paymentType;//支付方式

    private Long paymentAppId;//支付应用ID

    private String paymentChannelCode;//支付渠道编码

    @Version
    private Integer version;//乐观锁版本号
}
