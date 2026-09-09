package cc.ivera.order.interfaces.vo;

import lombok.Data;

import java.util.Date;

/**
 * 物流时间线 VO。
 */
@Data
public class ShipmentTimelineVO {

    private Date SHIPPED;

    private Date IN_TRANSIT;

    private Date DELIVERED;

    private Date RECEIVED;
}
