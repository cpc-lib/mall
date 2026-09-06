package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 物流单（V2）：通过 LogisticsProvider 接口对接（当前为模拟物流商），
 * 定时任务模拟运输推进，用户确认收货后履约完成。
 */
@Data
@TableName("t_order_shipment")
public class OrderShipment extends BaseEntity {

    private String shipmentNo;//物流单编号

    private String orderNo;//商户订单编号

    private String logisticsCompany;//物流公司（模拟：模拟快递）

    private String trackingNo;//运单号（模拟物流商生成）

    private String status;//SHIPPED/IN_TRANSIT/DELIVERED/RECEIVED/CANCELLED

    private Date shippedTime;//发货时间

    private Date inTransitTime;//进入运输时间（模拟）

    private Date deliveredTime;//派送送达时间（模拟）

    private Date receivedTime;//用户确认收货时间

    private String remark;//备注
}
