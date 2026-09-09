package cc.ivera.order.interfaces;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.interfaces.vo.OrderListVO;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@CrossOrigin //开放前端的跨域访问
@Api(tags = "商品订单管理")
@RestController
@RequestMapping("/api/order-info")
@Validated
public class OrderInfoController {

    private final OrderInfoService orderInfoService;

    public OrderInfoController(
        OrderInfoService orderInfoService
    ) {
        this.orderInfoService = orderInfoService;
    }

    @ApiOperation("订单列表")
    @GetMapping("/list")
    public R<OrderListVO> list() {
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        OrderListVO vo = new OrderListVO();
        vo.setList(list);
        return R.ok(vo);
    }

    /**
     * 前端进行轮询去查询订单的状态
     * 微信回调服务器的接口去修改订单的状态
     * 查询本地订单状态 等待支付成功,关闭二维码页面
     *
     * @param orderNo
     * @return
     */
    @ApiOperation("查询本地订单状态")
    @GetMapping("/query-order-status/{orderNo}")
    public R<?> queryOrderStatus(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if (OrderStatus.SUCCESS.getType().equals(orderStatus)) {
            return R.ok().setMessage("支付成功"); //支付成功
        } else if (OrderStatus.NOTPAY.getType().equals(orderStatus)) {
            return R.ok().setCode(101).setMessage("支付中......");
        } else {
            return R.ok().setCode(101).setMessage("支付中......");
        }
    }

}
