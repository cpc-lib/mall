package cc.ivera.vo;

import lombok.Data;

/**
 * 订单物流详情 VO。
 */
@Data
public class ShipmentDetailVO {

    private String orderNo;

    private Boolean shipped;

    private String shipmentNo;

    private String logisticsCompany;

    private String trackingNo;

    private String status;

    private ShipmentTimelineVO timeline;
}
