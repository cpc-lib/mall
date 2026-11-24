package cc.ivera.payment.interfaces;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.PaymentInfoService;
import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.application.wxpay.WxPayOrderFacade;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.application.dto.WxPayNativeVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.util.HttpUtils;
import cc.ivera.shared.web.R;
import com.github.wxpay.sdk.WXPayUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@CrossOrigin //跨域
@RestController
@RequestMapping("/api/wx-pay-v2")
@Tag(name = "网站微信支付APIv2")
@Slf4j
@Validated
public class WxPayV2Controller {

    private static final String NOTIFY_IDEMPOTENT_KEY_PREFIX = "payment:wx:v2:notify:processed:";

    private static final long NOTIFY_IDEMPOTENT_EXPIRE_HOURS = 24L;

    private final WxPayOrderFacade wxPayOrderFacade;

    private final PaymentConfigGateway paymentConfigLoader;

    private final OrderPaymentApi orderPaymentApi;

    private final PaymentInfoService paymentInfoService;

    private final PaymentSuccessService paymentSuccessService;

    private final DistributedLockTemplate distributedLockTemplate;

    private final TransactionTemplate transactionTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    public WxPayV2Controller(
        WxPayOrderFacade wxPayOrderFacade,
        PaymentConfigGateway paymentConfigLoader,
        OrderPaymentApi orderPaymentApi,
        PaymentInfoService paymentInfoService,
        PaymentSuccessService paymentSuccessService,
        DistributedLockTemplate distributedLockTemplate,
        TransactionTemplate transactionTemplate,
        StringRedisTemplate stringRedisTemplate
    ) {
        this.wxPayOrderFacade = wxPayOrderFacade;
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderPaymentApi = orderPaymentApi;
        this.paymentInfoService = paymentInfoService;
        this.paymentSuccessService = paymentSuccessService;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * Native下单
     *
     * @param productId
     * @return
     */
    @Operation(summary = "调用统一下单API，生成支付二维码")
    @PostMapping("/native/{productId}")
    public R<WxPayNativeVO> createNative(@PathVariable @Positive(message = "商品ID必须大于0") Long productId,
                                         @RequestParam(required = false) Long paymentAppId,
                                         HttpServletRequest request) {
        log.info("发起支付请求 v2，productId={}, paymentAppId={}", productId, paymentAppId);

        String remoteAddr = request.getRemoteAddr();
        //修改code_url可以重新获取到新的地址信息
        WxPayNativeVO map = wxPayOrderFacade.nativePayV2(productId, remoteAddr, paymentAppId);
        return R.ok(map);
    }

    /**
     * 支付通知
     * 微信支付通过支付通知接口将用户支付成功消息通知给商户
     */
    @PostMapping("/native/notify")
    public String wxNotify(HttpServletRequest request) {
        log.info("微信支付v2发送的回调");

        try {
            //处理通知参数
            String body = HttpUtils.readData(request);

            //解析xml数据
            Map<String, String> notifyMap = WXPayUtil.xmlToMap(body);

            //获取微信支付流水号作为通知ID
            String transactionId = notifyMap.get("transaction_id");
            log.info("微信支付v2通知transactionId ===> {}", transactionId);

            //幂等检查
            if (!tryAcquireNotifyLock(transactionId)) {
                log.info("微信支付v2通知已处理或正在处理中，直接返回成功，transactionId={}", transactionId);
                return wxNotifySuccess();
            }

            try {
                //获取商户订单号（先解析用于确定验签密钥来源）
                String orderNo = notifyMap.get("out_trade_no");

                //验签：partnerKey 来自渠道表，按 订单→支付应用→渠道默认 链路解析
                String partnerKey = resolvePartnerKey(orderNo);
                if (!WXPayUtil.isSignatureValid(body, partnerKey)) {
                    log.error("通知验签失败");
                    releaseNotifyLock(transactionId);
                    return wxNotifyFail("验签失败");
                }

                //判断通信和业务是否成功
                if (!"SUCCESS".equals(notifyMap.get("return_code")) || !"SUCCESS".equals(notifyMap.get("result_code"))) {
                    log.error("微信支付v2通知结果失败");
                    releaseNotifyLock(transactionId);
                    return wxNotifyFail("失败");
                }

                if (orderNo == null || orderNo.trim().isEmpty()) {
                    log.error("微信支付v2通知缺少商户订单号");
                    releaseNotifyLock(transactionId);
                    return wxNotifyFail("订单号不能为空");
                }

                // 金额格式先做基础校验，和本地订单金额的一致性校验放到加锁事务内完成。
                Long notifyTotalFee = parseTotalFee(notifyMap.get("total_fee"));
                if (notifyTotalFee == null) {
                    log.error("微信支付v2通知金额格式错误");
                    releaseNotifyLock(transactionId);
                    return wxNotifyFail("金额校验失败");
                }

                // 订单级支付锁：同一订单的支付通知/查单/关单/支付单推进共用一把锁。
                String lockKey = "payment:order:" + orderNo;

                // 使用 Redisson 分布式锁（leaseTime=-1 开启看门狗自动续期）
                distributedLockTemplate.execute(lockKey, 5000L, () -> {
                    OrderInfo order = orderPaymentApi.getOrderByOrderNo(orderNo);
                    if (order == null) {
                        throw new BizException("微信支付v2通知订单不存在，orderNo=" + orderNo);
                    }
                    if (notifyTotalFee == null || !notifyTotalFee.equals(order.getTotalFee().longValue())) {
                        throw new BizException("微信支付v2通知金额与本地订单金额不一致，orderNo=" + orderNo);
                    }
                    transactionTemplate.executeWithoutResult(status ->
                        doProcessWxPayV2NotifyInTransaction(order, transactionId, notifyTotalFee, notifyMap, body));
                });

                markNotifyProcessed(transactionId);
                log.info("微信支付v2通知处理成功，已应答");
                return wxNotifySuccess();
            } catch (Exception e) {
                releaseNotifyLock(transactionId);
                throw e;
            }
        } catch (Exception e) {
            log.error("处理微信支付v2通知失败", e);
            return wxNotifyFail("失败");
        }
    }

    /**
     * V2 回调验签密钥解析：订单绑定的支付应用优先，否则取微信渠道默认应用；密钥均来自渠道表 partner_key。
     */
    private String resolvePartnerKey(String orderNo) {
        PaymentAppConfig config = null;
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            OrderInfo orderInfo = orderPaymentApi.getOrderByOrderNo(orderNo);
            if (orderInfo != null && orderInfo.getPaymentAppId() != null) {
                config = paymentConfigLoader.getAppConfig(orderInfo.getPaymentAppId());
            }
        }
        if (config == null) {
            config = paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigGateway.CHANNEL_WXPAY);
        }
        if (config == null || config.getPartnerKey() == null || config.getPartnerKey().trim().isEmpty()) {
            throw new BizException("微信APIv2密钥partnerKey未配置");
        }
        return config.getPartnerKey();
    }

    private boolean tryAcquireNotifyLock(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            return true;
        }
        String key = NOTIFY_IDEMPOTENT_KEY_PREFIX + transactionId;
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, "processing", NOTIFY_IDEMPOTENT_EXPIRE_HOURS, TimeUnit.HOURS);
        return Boolean.TRUE.equals(success);
    }

    private void releaseNotifyLock(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            return;
        }
        String key = NOTIFY_IDEMPOTENT_KEY_PREFIX + transactionId;
        String currentValue = stringRedisTemplate.opsForValue().get(key);
        if ("processing".equals(currentValue)) {
            stringRedisTemplate.delete(key);
        }
    }

    private void markNotifyProcessed(String transactionId) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            return;
        }
        String key = NOTIFY_IDEMPOTENT_KEY_PREFIX + transactionId;
        stringRedisTemplate.opsForValue().set(key, "processed", NOTIFY_IDEMPOTENT_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    private void doProcessWxPayV2NotifyInTransaction(OrderInfo order,
                                                     String transactionId,
                                                     Long notifyTotalFee,
                                                     Map<String, String> notifyMap,
                                                     String body) {
        String orderNo = order.getOrderNo();
        log.info("微信支付v2通知加锁处理开始，orderNo={}, transactionId={}", orderNo, transactionId);

        // V2：统一支付成功处理器（支付单状态机 + 订单 CAS + 预占提交 + 异常冲正）。
        // 只有订单首次成交推进成功才写支付流水。
        boolean firstSettled = paymentSuccessService.handlePaymentSuccess(
            order, PayType.WXPAY.getType(), transactionId, notifyTotalFee == null ? null : notifyTotalFee.intValue());
        if (firstSettled) {
            paymentInfoService.createPaymentInfoForWxPayV2(notifyMap, body);
            log.info("微信支付v2通知处理完成，orderNo={}, transactionId={}", orderNo, transactionId);
        } else {
            log.info("微信支付v2通知幂等/冲正路径 ===> orderNo={}, transactionId={}", orderNo, transactionId);
        }
    }

    private String wxNotifySuccess() {
        return wxNotifyResponse("SUCCESS", "OK");
    }

    private String wxNotifyFail(String message) {
        return wxNotifyResponse("FAIL", message);
    }

    private String wxNotifyResponse(String returnCode, String returnMsg) {
        Map<String, String> returnMap = new HashMap<>();
        returnMap.put("return_code", returnCode);
        returnMap.put("return_msg", returnMsg);
        try {
            return WXPayUtil.mapToXml(returnMap);
        } catch (Exception e) {
            log.error("构造微信支付v2通知响应失败", e);
            return "<xml><return_code><![CDATA[" + returnCode + "]]></return_code><return_msg><![CDATA[" + returnMsg + "]]></return_msg></xml>";
        }
    }

    private Long parseTotalFee(String totalFee) {
        try {
            return Long.parseLong(totalFee);
        } catch (NumberFormatException e) {
            log.error("微信支付v2通知金额格式错误 ===> {}", totalFee, e);
            return null;
        }
    }
}
