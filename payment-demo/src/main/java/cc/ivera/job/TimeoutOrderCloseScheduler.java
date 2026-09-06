package cc.ivera.job;

import cc.ivera.entity.OrderInfo;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayType;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.service.AliPayService;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class TimeoutOrderCloseScheduler {
    private final OrderInfoMapper orderInfoMapper;
    private final AliPayService aliPayService;
    private final WxPayOrderFacade wxPayOrderFacade;

    public TimeoutOrderCloseScheduler(OrderInfoMapper orderInfoMapper, AliPayService aliPayService, WxPayOrderFacade wxPayOrderFacade) {
        this.orderInfoMapper = orderInfoMapper;
        this.aliPayService = aliPayService;
        this.wxPayOrderFacade = wxPayOrderFacade;
    }

    @Scheduled(fixedDelayString = "${payment.order.timeout-scan-ms:60000}")
    public void scan() {
        Date cutoff = new Date(System.currentTimeMillis() - 15L * 60L * 1000L);
        QueryWrapper<OrderInfo> q = new QueryWrapper<>();
        q.eq("legacy_status", OrderStatus.NOTPAY.getType())
                .lt("create_time", cutoff)
                .orderByAsc("create_time")
                .last("limit 100");
        List<OrderInfo> orders;
        try {
            orders = orderInfoMapper.selectList(q);
        } catch (RuntimeException dmLimitSyntax) {
            // DM8 对 LIMIT 语法兼容性依赖模式；退化为不带 LIMIT 的扫描，不改变业务时序。
            q = new QueryWrapper<>();
            q.eq("legacy_status", OrderStatus.NOTPAY.getType()).lt("create_time", cutoff).orderByAsc("create_time");
            orders = orderInfoMapper.selectList(q);
        }
        for (OrderInfo order : orders) {
            try {
                // 绝不直接改本地状态：必须让渠道服务完成“查渠道 -> 必要时关渠道 -> 再条件更新本地”。
                if (PayType.ALIPAY.getType().equals(order.getPaymentType())) {
                    aliPayService.checkOrderStatus(order.getOrderNo());
                } else if (PayType.WXPAY.getType().equals(order.getPaymentType())) {
                    wxPayOrderFacade.checkOrderStatus(order.getOrderNo());
                }
            } catch (RuntimeException e) {
                log.error("超时关单对账失败，保持本地订单原状态等待下轮重试，orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
