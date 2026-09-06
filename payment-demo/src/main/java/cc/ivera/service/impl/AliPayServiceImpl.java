package cc.ivera.service.impl;

import cc.ivera.config.AlipayProperties;
import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.RefundInfo;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayType;
import cc.ivera.enums.RefundStatus;
import cc.ivera.enums.alipay.AliPayTradeState;
import cc.ivera.exception.BizException;
import cc.ivera.lock.DistributedLockTemplate;
import cc.ivera.service.AliPayService;
import cc.ivera.service.OrderInfoService;
import cc.ivera.service.PaymentInfoService;
import cc.ivera.service.PaymentOrderService;
import cc.ivera.service.PaymentSuccessService;
import cc.ivera.service.RefundInfoService;
import cc.ivera.service.refund.RefundStatusSyncResult;
import cc.ivera.util.JsonUtils;
import cc.ivera.util.MoneyUtils;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.AlipayConstants;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AliPayServiceImpl implements AliPayService {

    private static final String ALIPAY_REFUND_SUCCESS = "REFUND_SUCCESS";

    /**
     * 支付宝查单子错误码：交易不存在（从未发起支付，渠道侧无此单）。
     */
    private static final String ALIPAY_SUB_CODE_TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private final OrderInfoService orderInfoService;

    private final AlipayClient alipayClient;

    private final AlipayProperties alipayProperties;

    private final PaymentConfigLoader paymentConfigLoader;

    private final PaymentInfoService paymentInfoService;

    private final RefundInfoService refundInfoService;

    private final PaymentOrderService paymentOrderService;

    private final PaymentSuccessService paymentSuccessService;

    private final DistributedLockTemplate distributedLockTemplate;

    private final TransactionTemplate transactionTemplate;

    public AliPayServiceImpl(
        OrderInfoService orderInfoService,
        AlipayClient alipayClient,
        AlipayProperties alipayProperties,
        PaymentConfigLoader paymentConfigLoader,
        PaymentInfoService paymentInfoService,
        RefundInfoService refundInfoService,
        PaymentOrderService paymentOrderService,
        PaymentSuccessService paymentSuccessService,
        DistributedLockTemplate distributedLockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.orderInfoService = orderInfoService;
        this.alipayClient = alipayClient;
        this.alipayProperties = alipayProperties;
        this.paymentConfigLoader = paymentConfigLoader;
        this.paymentInfoService = paymentInfoService;
        this.refundInfoService = refundInfoService;
        this.paymentOrderService = paymentOrderService;
        this.paymentSuccessService = paymentSuccessService;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String tradeCreate(Long productId) {
        return tradeCreate(productId, null);
    }

    @Override
    public String tradeCreate(Long productId, Long paymentAppId) {
        return distributedLockTemplate.execute(
                "payment:ali:pagepay:" + productId + ":" + (paymentAppId == null ? "default" : paymentAppId),
                3000L,
                15000L,
                () -> doTradeCreate(productId, paymentAppId)
        );
    }

    private String doTradeCreate(Long productId, Long paymentAppId) {
        try {
            log.info("生成支付宝订单");
            PaymentAppConfig payConfig = resolveAliPayConfig(paymentAppId);
            OrderInfo orderInfo = orderInfoService.createOrReuseOrder(
                    productId,
                    PayType.ALIPAY.getType(),
                    payConfig.getAppId(),
                    PaymentConfigLoader.CHANNEL_ALIPAY
            );
            // V2：发起支付前创建支付单（临近过期拒绝）。
            cc.ivera.entity.PaymentOrder paymentOrder = paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_ALIPAY);

            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(required(payConfig.getAlipayNotifyUrl(), "支付宝notifyUrl未配置"));
            request.setReturnUrl(required(payConfig.getReturnUrl(), "支付宝returnUrl未配置"));

            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderInfo.getOrderNo());
            bizContent.put("total_amount", MoneyUtils.centsToYuan(orderInfo.getTotalFee()));
            bizContent.put("subject", orderInfo.getTitle());
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
            request.setBizContent(JsonUtils.toJson(bizContent));

            AlipayTradePagePayResponse response = buildAlipayClient(payConfig).pageExecute(request);
            if (response.isSuccess()) {
                log.info("支付宝下单成功，返回结果 ===> {}", response.getBody());
                paymentOrderService.markPaying(paymentOrder.getPaymentNo(), null);
                return response.getBody();
            }

            log.info("支付宝下单失败，返回码 ===> {}, 返回描述 ===> {}", response.getCode(), response.getMsg());
            throw new BizException("创建支付宝支付交易失败：" + response.getSubMsg());
        } catch (AlipayApiException e) {
            log.error("创建支付宝支付交易失败", e);
            throw new BizException("创建支付宝支付交易失败", e);
        }
    }

    @Override
    public String tradeCreateByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("订单号不能为空");
        }
        return distributedLockTemplate.execute(
                "payment:ali:pagepay:order:" + orderNo,
                3000L,
                15000L,
                () -> {
                    try {
                        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
                        if (orderInfo == null) {
                            throw new BizException("订单不存在，orderNo=" + orderNo);
                        }
                        if (!PayType.ALIPAY.getType().equals(orderInfo.getPaymentType())) {
                            throw new BizException("订单支付方式不是支付宝，orderNo=" + orderNo);
                        }
                        if (!OrderStatus.NOTPAY.getType().equals(orderInfo.getLegacyStatus())) {
                            throw new BizException("订单当前状态不可支付：" + orderInfo.getLegacyStatus());
                        }
                        PaymentAppConfig payConfig = resolveAliPayConfigByOrderNo(orderNo);
                        // V2：发起支付前创建支付单（临近过期在此拒绝）。
                        cc.ivera.entity.PaymentOrder paymentOrder = paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_ALIPAY);
                        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
                        request.setNotifyUrl(required(payConfig.getAlipayNotifyUrl(), "支付宝notifyUrl未配置"));
                        request.setReturnUrl(required(payConfig.getReturnUrl(), "支付宝returnUrl未配置"));
                        Map<String, Object> bizContent = new HashMap<>();
                        bizContent.put("out_trade_no", orderInfo.getOrderNo());
                        bizContent.put("total_amount", MoneyUtils.centsToYuan(orderInfo.getTotalFee()));
                        bizContent.put("subject", orderInfo.getTitle());
                        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
                        request.setBizContent(JsonUtils.toJson(bizContent));
                        AlipayTradePagePayResponse response = buildAlipayClient(payConfig).pageExecute(request);
                        if (!response.isSuccess()) {
                            throw new BizException("创建支付宝支付交易失败：" + response.getSubMsg());
                        }
                        paymentOrderService.markPaying(paymentOrder.getPaymentNo(), null);
                        return response.getBody();
                    } catch (AlipayApiException e) {
                        throw new BizException("创建支付宝支付交易失败", e);
                    }
                }
        );
    }

    @Override
    public void processOrder(Map<String, String> params) {
        log.info("处理支付宝支付通知");

        String orderNo = params.get("out_trade_no");
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new BizException("支付宝支付通知缺少商户订单号");
        }

        String notifyId = params.get("notify_id");
        String lockKey = "payment:ali:notify:pay:" + orderNo;

        distributedLockTemplate.execute(lockKey, 5000L, 30000L, () ->
                transactionTemplate.execute(status -> {
                    doProcessAliPayNotifyInTransaction(params, orderNo, notifyId);
                    return null;
                })
        );
    }

    private void doProcessAliPayNotifyInTransaction(Map<String, String> params, String orderNo, String notifyId) {
        log.info("支付宝支付通知加锁处理开始，orderNo={}, notifyId={}", orderNo, notifyId);

        // 支付宝异步通知同样可能重复投递，和主动查单、关单并发。
        // Redis 分布式锁 + 数据库行锁 + 状态条件更新共同保证幂等。
        OrderInfo lockedOrder = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
        if (lockedOrder == null) {
            throw new BizException("支付宝支付通知对应订单不存在，orderNo=" + orderNo);
        }
        validateAliPayOrderNotify(lockedOrder, params);

        // V2：统一支付成功处理器（支付单状态机 + 订单 CAS + 预占提交 + 异常冲正）。
        String tradeNo = params.get("trade_no");
        Integer paidAmount = MoneyUtils.yuanToCents(params.get("total_amount"));
        boolean firstSettled = paymentSuccessService.handlePaymentSuccess(
                orderNo, PaymentConfigLoader.CHANNEL_ALIPAY, tradeNo, paidAmount);
        if (firstSettled) {
            paymentInfoService.createPaymentInfoForAliPay(params);
            log.info("支付宝支付通知处理完成，orderNo={}, notifyId={}", orderNo, notifyId);
        } else {
            log.info("支付宝支付通知幂等/冲正路径 ===> orderNo={}, notifyId={}", orderNo, notifyId);
        }
    }


    private void validateAliPayOrderNotify(OrderInfo orderInfo, Map<String, String> params) {
        if (!PayType.ALIPAY.getType().equals(orderInfo.getPaymentType())) {
            throw new BizException("支付宝支付通知支付类型不匹配，orderNo=" + orderInfo.getOrderNo());
        }

        String totalAmount = params.get("total_amount");
        if (totalAmount == null || totalAmount.trim().isEmpty()) {
            log.warn("支付宝支付通知缺少金额字段，跳过金额校验，orderNo={}", orderInfo.getOrderNo());
            return;
        }

        int notifyTotal = MoneyUtils.yuanToCents(totalAmount);
        if (!Integer.valueOf(notifyTotal).equals(orderInfo.getTotalFee())) {
            throw new BizException("支付宝支付通知金额与订单金额不一致，orderNo=" + orderInfo.getOrderNo());
        }
    }

    @Override
    public void cancelOrder(String orderNo) {
        if (!OrderStatus.NOTPAY.getType().equals(orderInfoService.getOrderStatus(orderNo))) {
            log.info("订单当前状态不允许取消，orderNo={}", orderNo);
            return;
        }

        closeOrder(orderNo);
        boolean updated = orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CANCEL);
        if (!updated) {
            log.info("订单取消状态更新被忽略，orderNo={}", orderNo);
        }
    }

    @Override
    public String queryOrder(String orderNo) {
        try {
            log.info("调用支付宝查单接口 ===> {}", orderNo);

            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderNo);
            request.setBizContent(JsonUtils.toJson(bizContent));

            AlipayTradeQueryResponse response = buildAlipayClient(resolveAliPayConfigByOrderNo(orderNo)).execute(request);
            if (response.isSuccess()) {
                log.info("支付宝查单成功，返回结果 ===> {}", response.getBody());
                return response.getBody();
            }

            log.info("支付宝查单失败，返回码 ===> {}, 子码 ===> {}, 返回描述 ===> {}", response.getCode(), response.getSubCode(), response.getMsg());
            // ACQ.TRADE_NOT_EXIST 表示渠道侧从无此交易（从未发起支付），返回 body 由对账链路按"渠道无单"关单；
            // 其余失败（系统错误/限流等）状态不确定，返回 null 保持本地 NOTPAY 等待下轮对账，避免单边账。
            if (ALIPAY_SUB_CODE_TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return response.getBody();
            }
            return null;
        } catch (AlipayApiException e) {
            log.error("调用支付宝查单接口失败", e);
            throw new BizException("查单接口调用失败", e);
        }
    }

    @Override
    public void checkOrderStatus(String orderNo) {
        log.warn("根据订单号核实支付宝订单状态 ===> {}", orderNo);

        String result = queryOrder(orderNo);
        if (result == null) {
            // 查单无法获得明确渠道状态时，绝不能直接关闭本地订单；保持 NOTPAY 等待下一轮对账，避免单边账。
            log.warn("支付宝查单未获得明确交易状态，本地订单保持未支付等待重试 ===> {}", orderNo);
            return;
        }

        Map<String, Object> resultMap = JsonUtils.toObjectMap(result);
        Map<String, Object> alipayTradeQueryResponse = JsonUtils.toObjectMap(resultMap.get("alipay_trade_query_response"));
        if (alipayTradeQueryResponse == null) {
            log.warn("支付宝查单响应缺少交易信息 ===> {}", orderNo);
            return;
        }

        String tradeStatus = (String) alipayTradeQueryResponse.get("trade_status");
        String subCode = (String) alipayTradeQueryResponse.get("sub_code");
        if (tradeStatus == null && ALIPAY_SUB_CODE_TRADE_NOT_EXIST.equals(subCode)) {
            // 渠道侧从无此交易（从未发起支付）：渠道无单可关（关单接口对不存在交易仅返回失败，不抛异常），
            // 直接走本地 V2 关单链路（CAS NOTPAY->CLOSED：关闭活跃支付单 + 释放预占库存）。
            log.warn("支付宝核实渠道无此交易（从未发起支付），本地订单超时关单 ===> {}", orderNo);
            closeOrder(orderNo);
            orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
            return;
        }

        if (AliPayTradeState.NOTPAY.getType().equals(tradeStatus)) {
            log.warn("支付宝核实订单未支付 ===> {}", orderNo);
            closeOrder(orderNo);
            orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
            return;
        }

        if (AliPayTradeState.SUCCESS.getType().equals(tradeStatus)) {
            log.warn("支付宝核实订单已支付 ===> {}", orderNo);
            // V2：查单确认支付成功也走统一支付成功处理器。
            String tradeNo = (String) alipayTradeQueryResponse.get("trade_no");
            String totalAmount = (String) alipayTradeQueryResponse.get("total_amount");
            Integer paidAmount = totalAmount == null ? null : MoneyUtils.yuanToCents(totalAmount);
            boolean firstSettled = paymentSuccessService.handlePaymentSuccess(
                    orderNo, PaymentConfigLoader.CHANNEL_ALIPAY, tradeNo, paidAmount);
            if (firstSettled) {
                paymentInfoService.createPaymentInfoForAliPay(alipayTradeQueryResponse);
            }
        }
    }

    @Override
    public void executeRefund(RefundInfo refundInfo) {
        try {
            if (refundInfo == null) {
                throw new BizException("退款申请单不能为空");
            }

            log.info("调用支付宝退款API, refundNo = {}", refundInfo.getRefundNo());

            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", refundInfo.getOrderNo());
            bizContent.put("out_request_no", refundInfo.getRefundNo());
            bizContent.put("refund_amount", MoneyUtils.centsToYuan(refundInfo.getRefund()));
            bizContent.put("refund_reason", refundInfo.getReason());
            request.setBizContent(JsonUtils.toJson(bizContent));

            AlipayTradeRefundResponse response = buildAlipayClient(resolveAliPayConfigByOrderNo(refundInfo.getOrderNo())).execute(request);
            if (response.isSuccess()) {
                log.info("支付宝退款成功，返回结果 ===> {}", response.getBody());
                refundInfoService.updateRefundToSuccess(refundInfo.getRefundNo(), response.getTradeNo(), response.getBody());
                return;
            }

            log.info("支付宝退款失败，返回码 ===> {}, 返回描述 ===> {}", response.getCode(), response.getMsg());
            refundInfoService.updateRefundToFailed(refundInfo.getRefundNo(), response.getBody());
            throw new BizException("创建支付宝退款申请失败：" + response.getSubMsg());
        } catch (AlipayApiException e) {
            log.error("调用支付宝退款接口失败", e);
            throw new BizException("创建支付宝退款申请失败", e);
        }
    }

    @Override
    public String queryRefund(String refundNo) {
        AlipayTradeFastpayRefundQueryResponse response = executeRefundQuery(getRefundInfoOrThrow(refundNo));
        return response == null ? null : response.getBody();
    }

    @Override
    public RefundStatusSyncResult queryRefundStatusForSync(String refundNo) {
        RefundInfo refundInfo = getRefundInfoOrThrow(refundNo);
        AlipayTradeFastpayRefundQueryResponse response = executeRefundQuery(refundInfo);
        if (response == null) {
            throw new BizException("支付宝退款查询无结果");
        }

        String channelStatus = response.getRefundStatus();
        if (channelStatus == null || channelStatus.trim().isEmpty()) {
            log.info("支付宝退款查询暂未返回可同步状态，refundNo={}", refundNo);
        }

        return RefundStatusSyncResult.of(
                firstNonBlank(response.getOutTradeNo(), refundInfo.getOrderNo()),
                firstNonBlank(response.getOutRequestNo(), refundNo),
                response.getTradeNo(),
                channelStatus,
                mapAliPayRefundStatus(channelStatus),
                response.getBody(),
                parseYuanToCents(response.getTotalAmount()),
                parseYuanToCents(response.getRefundAmount()));
    }

    @Override
    public String queryBill(String billDate, String type) {
        try {
            AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("bill_type", type);
            bizContent.put("bill_date", billDate);
            request.setBizContent(JsonUtils.toJson(bizContent));

            AlipayDataDataserviceBillDownloadurlQueryResponse response = buildAlipayClient(resolveAliPayConfig(null)).execute(request);
            if (!response.isSuccess()) {
                log.info("支付宝申请账单失败，返回码 ===> {}, 返回描述 ===> {}", response.getCode(), response.getMsg());
                throw new BizException("申请支付宝账单失败");
            }

            log.info("支付宝申请账单成功，返回结果 ===> {}", response.getBody());
            Map<String, Object> resultMap = JsonUtils.toObjectMap(response.getBody());
            Map<String, Object> billDownloadurlResponse = JsonUtils.toObjectMap(
                    resultMap.get("alipay_data_dataservice_bill_downloadurl_query_response"));
            if (billDownloadurlResponse == null) {
                throw new BizException("申请支付宝账单失败");
            }

            String billDownloadUrl = (String) billDownloadurlResponse.get("bill_download_url");
            if (billDownloadUrl == null || billDownloadUrl.trim().isEmpty()) {
                throw new BizException("申请支付宝账单失败");
            }
            return billDownloadUrl;
        } catch (AlipayApiException e) {
            log.error("调用支付宝账单接口失败", e);
            throw new BizException("申请支付宝账单失败", e);
        }
    }

    private void closeOrder(String orderNo) {
        try {
            log.info("调用支付宝关单接口，订单号 ===> {}", orderNo);

            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", orderNo);
            request.setBizContent(JsonUtils.toJson(bizContent));

            AlipayTradeCloseResponse response = buildAlipayClient(resolveAliPayConfigByOrderNo(orderNo)).execute(request);
            if (response.isSuccess()) {
                log.info("支付宝关单成功，返回结果 ===> {}", response.getBody());
            } else {
                log.info("支付宝关单失败，返回码 ===> {}, 返回描述 ===> {}", response.getCode(), response.getMsg());
            }
        } catch (AlipayApiException e) {
            log.error("调用支付宝关单接口失败", e);
            throw new BizException("关单接口调用失败", e);
        }
    }

    private RefundInfo getRefundInfoOrThrow(String refundNo) {
        RefundInfo refundInfo = refundInfoService.getByRefundNo(refundNo);
        if (refundInfo == null) {
            throw new BizException("退款单不存在");
        }
        return refundInfo;
    }

    private AlipayTradeFastpayRefundQueryResponse executeRefundQuery(RefundInfo refundInfo) {
        try {
            log.info("调用支付宝退款查询接口 ===> {}", refundInfo.getRefundNo());

            AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
            Map<String, Object> bizContent = new HashMap<>();
            bizContent.put("out_trade_no", refundInfo.getOrderNo());
            bizContent.put("out_request_no", refundInfo.getRefundNo());
            request.setBizContent(JsonUtils.toJson(bizContent));

            AlipayTradeFastpayRefundQueryResponse response = buildAlipayClient(resolveAliPayConfigByOrderNo(refundInfo.getOrderNo())).execute(request);
            if (response.isSuccess()) {
                log.info("支付宝退款查询成功，返回结果 ===> {}", response.getBody());
                return response;
            }

            log.info("支付宝退款查询失败，返回码 ===> {}, 返回描述 ===> {}", response.getCode(), response.getMsg());
            return null;
        } catch (AlipayApiException e) {
            log.error("调用支付宝退款查询接口失败", e);
            throw new BizException("退款查询接口调用失败", e);
        }
    }

    private PaymentAppConfig resolveAliPayConfig(Long paymentAppId) {
        PaymentAppConfig config = paymentAppId == null
                ? paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigLoader.CHANNEL_ALIPAY)
                : paymentConfigLoader.getRequiredAppConfig(paymentAppId);
        if (config == null) {
            return buildDefaultAliPayConfig();
        }
        if (!PaymentConfigLoader.CHANNEL_ALIPAY.equals(config.getChannelCode())) {
            throw new BizException("支付应用不是支付宝渠道");
        }
        return config;
    }

    private PaymentAppConfig resolveAliPayConfigByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        return resolveAliPayConfig(orderInfo == null ? null : orderInfo.getPaymentAppId());
    }

    private PaymentAppConfig buildDefaultAliPayConfig() {
        PaymentAppConfig config = new PaymentAppConfig();
        config.setChannelCode(PaymentConfigLoader.CHANNEL_ALIPAY);
        config.setAlipayAppId(alipayProperties.getAppId());
        config.setSellerId(alipayProperties.getSellerId());
        config.setGatewayUrl(alipayProperties.getGatewayUrl());
        config.setMerchantPrivateKey(alipayProperties.getMerchantPrivateKey());
        config.setAlipayPublicKey(alipayProperties.getAlipayPublicKey());
        config.setContentKey(alipayProperties.getContentKey());
        config.setReturnUrl(alipayProperties.getReturnUrl());
        config.setAlipayNotifyUrl(alipayProperties.getNotifyUrl());
        return config;
    }

    private AlipayClient buildAlipayClient(PaymentAppConfig payConfig) throws AlipayApiException {
        AlipayConfig alipayConfig = new AlipayConfig();
        alipayConfig.setServerUrl(required(payConfig.getGatewayUrl(), "支付宝gatewayUrl未配置"));
        alipayConfig.setAppId(required(payConfig.getAlipayAppId(), "支付宝appId未配置"));
        alipayConfig.setPrivateKey(required(payConfig.getMerchantPrivateKey(), "支付宝merchantPrivateKey未配置"));
        alipayConfig.setFormat(AlipayConstants.FORMAT_JSON);
        alipayConfig.setCharset(AlipayConstants.CHARSET_UTF8);
        alipayConfig.setAlipayPublicKey(required(payConfig.getAlipayPublicKey(), "支付宝alipayPublicKey未配置"));
        alipayConfig.setSignType(AlipayConstants.SIGN_TYPE_RSA2);
        return new DefaultAlipayClient(alipayConfig);
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private RefundStatus mapAliPayRefundStatus(String channelStatus) {
        if (ALIPAY_REFUND_SUCCESS.equals(channelStatus)) {
            return RefundStatus.SUCCESS;
        }
        return null;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.trim().isEmpty() ? fallback : primary;
    }

    private Integer parseYuanToCents(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            return null;
        }
        return MoneyUtils.yuanToCents(amount);
    }
}
