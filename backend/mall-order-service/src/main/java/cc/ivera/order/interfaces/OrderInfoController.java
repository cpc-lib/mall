package cc.ivera.order.interfaces;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.application.vo.OrderListVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.shared.web.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@CrossOrigin //开放前端的跨域访问
@Tag(name = "商品订单管理")
@RestController
@RequestMapping("/api/order-info")
@Validated
public class OrderInfoController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final OrderInfoService orderInfoService;

    public OrderInfoController(
        OrderInfoService orderInfoService
    ) {
        this.orderInfoService = orderInfoService;
    }

    @Operation(summary = "管理员：订单列表（全库）")
    @GetMapping("/list")
    public R<OrderListVO> list() {
        // 仅管理员可查全库订单，含收货人隐私信息
        String role = AuthContext.get().getRole();
        if (!ROLE_ADMIN.equals(role)) throw new BizException("无权访问");
        List<OrderInfo> list = orderInfoService.listOrderByCreateTimeDesc();
        OrderListVO vo = new OrderListVO();
        vo.setList(list);
        return R.ok(vo);
    }

    /**
     * 前端轮询查订单状态，关闭支付二维码弹窗。
     * 管理员可 probe 任意订单；普通用户必须是订单 owner。
     *
     * @param orderNo
     * @return
     */
    @Operation(summary = "查询本地订单状态（需为订单 owner 或管理员）")
    @GetMapping("/query-order-status/{orderNo}")
    public R<?> queryOrderStatus(@PathVariable @NotBlank(message = "订单号不能为空") @Size(max = 50, message = "订单号长度不能超过50个字符") String orderNo) {
        // 先查订单归属，普通用户必须是 owner
        String role = AuthContext.get().getRole();
        if (!ROLE_ADMIN.equals(role)) {
            OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
            if (order == null || !order.getUserId().equals(AuthContext.userId()))
                throw new BizException("订单不存在或无权访问");
        }
        String orderStatus = orderInfoService.getOrderStatus(orderNo);
        if (OrderStatus.SUCCESS.getType().equals(orderStatus)) {
            return R.ok().setMessage("支付成功");
        } else {
            return R.ok().setCode(101).setMessage("支付中......");
        }
    }

}
