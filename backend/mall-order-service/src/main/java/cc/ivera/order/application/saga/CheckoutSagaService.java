package cc.ivera.order.application.saga;

import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.impl.CheckoutTxService;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import org.apache.seata.saga.engine.StateMachineEngine;
import org.apache.seata.saga.statelang.domain.ExecutionStatus;
import org.apache.seata.saga.statelang.domain.StateMachineInstance;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 下单 Saga 入口：生成订单号并驱动 Seata 状态机（落单 → 预占库存 → 清空购物车）。
 * 任一正向步骤失败会按状态机定义反向补偿；补偿完成后向上抛出可读业务异常。
 */
@Service
public class CheckoutSagaService {

    private static final String STATE_MACHINE = "order-checkout";

    private final StateMachineEngine stateMachineEngine;
    private final CheckoutTxService checkoutTxService;

    public CheckoutSagaService(StateMachineEngine stateMachineEngine, CheckoutTxService checkoutTxService) {
        this.stateMachineEngine = stateMachineEngine;
        this.checkoutTxService = checkoutTxService;
    }

    /**
     * 下单：以 orderNo 作为 Saga 业务键，便于按订单号在 Seata 控制台/实例表追溯。
     */
    public OrderDetailVO checkout(Long userId, CheckoutRequest request) {
        String orderNo = OrderNoUtils.getOrderNo();
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("orderNo", orderNo);
        checkout.put("userId", userId);
        checkout.put("paymentType", request.getPaymentType());
        checkout.put("paymentAppId", request.getPaymentAppId());
        checkout.put("addressId", request.getAddressId());
        checkout.put("receiverName", request.getReceiverName());
        checkout.put("receiverPhone", request.getReceiverPhone());
        checkout.put("receiverAddress", request.getReceiverAddress());
        Map<String, Object> startParams = new LinkedHashMap<>();
        startParams.put("checkout", checkout);
        StateMachineInstance instance;
        try {
            instance = stateMachineEngine.startWithBusinessKey(STATE_MACHINE, null, orderNo, startParams);
        } catch (RuntimeException e) {
            throw new BizException("下单失败：" + rootMessage(e), e);
        }
        if (!ExecutionStatus.SU.equals(instance.getStatus())) {
            BizException failure = new BizException("下单失败：" + rootMessage(instance.getException()));
            compensate(instance, failure);
            throw failure;
        }
        return checkoutTxService.loadDetail(orderNo);
    }

    /**
     * 正向失败后显式触发反向补偿（CreateOrder -> CancelOrder，ReserveInventory -> ReleaseInventory）。
     * 状态机采用客户端补偿模式，补偿动作本身幂等；补偿失败时保留原始失败消息并追加原因。
     */
    private void compensate(StateMachineInstance instance, BizException failure) {
        try {
            StateMachineInstance compensated = stateMachineEngine.compensate(instance.getId(), null);
            if (!ExecutionStatus.SU.equals(compensated.getCompensationStatus())) {
                throw new BizException(failure.getMessage() + "；订单补偿未完成", compensated.getException());
            }
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(failure.getMessage() + "；订单补偿失败：" + rootMessage(e), e);
        }
    }

    /**
     * 取出最内层异常消息：Seata 会把动作异常包装为 EngineExecutionException，
     * 直接透出包装信息对用户不可读。
     */
    private static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
