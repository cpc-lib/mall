package cc.ivera.service.impl;

import cc.ivera.entity.LocalMessage;
import cc.ivera.mq.OrderCloseMessage;
import cc.ivera.service.LocalMessageService;
import cc.ivera.service.OrderCloseMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class OrderCloseMessageServiceImpl implements OrderCloseMessageService {

    private final LocalMessageService localMessageService;

    public OrderCloseMessageServiceImpl(LocalMessageService localMessageService) {
        this.localMessageService = localMessageService;
    }

    @Override
    public void sendCloseOrderMessage(String orderNo, String paymentType) {
        if (!StringUtils.hasText(orderNo) || !StringUtils.hasText(paymentType)) {
            throw new IllegalArgumentException("发送延迟关单消息失败，订单号或支付类型为空");
        }

        OrderCloseMessage message = new OrderCloseMessage();
        message.setOrderNo(orderNo);
        message.setPaymentType(paymentType);

        // 事务性发件箱：业务事务内落库 PENDING，提交后投递 MQ（发送者确认），消费成功后由消费者回写 CONSUMED
        localMessageService.saveAndPublishAfterCommit(LocalMessage.BIZ_TYPE_ORDER_CLOSE, orderNo, message);
        log.info("延迟关单消息已进入本地消息表发件箱，orderNo={}, paymentType={}", orderNo, paymentType);
    }
}
