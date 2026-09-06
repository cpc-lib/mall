package cc.ivera.service.impl.wxpay;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.entity.OrderInfo;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayType;
import cc.ivera.enums.wxpay.WxApiType;
import cc.ivera.enums.wxpay.WxNotifyType;
import cc.ivera.enums.wxpay.WxTradeState;
import cc.ivera.exception.BizException;
import cc.ivera.lock.DistributedLockTemplate;
import cc.ivera.service.OrderInfoService;
import cc.ivera.service.PaymentInfoService;
import cc.ivera.service.PaymentOrderService;
import cc.ivera.service.PaymentSuccessService;
import cc.ivera.service.wxpay.WxPayOrderFacade;
import cc.ivera.util.HttpClientUtils;
import cc.ivera.util.JsonUtils;
import cc.ivera.util.WxPayPrivateKeyUtil;
import cc.ivera.vo.WxPayJsapiVO;
import cc.ivera.vo.WxPayNativeVO;
import cc.ivera.vo.WxPayStatusVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.wxpay.sdk.WXPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class WxPayOrderService implements WxPayOrderFacade {

    private static final long PAY_NOTIFY_LOCK_WAIT_MS = 5000L;

    /**
     * 微信 V3 查单/关单响应错误码：渠道侧不存在此商户订单（从未发起支付）。
     */
    private static final String WX_ERR_CODE_ORDER_NOT_EXIST = "ORDER_NOT_EXIST";

    private final PaymentConfigLoader paymentConfigLoader;
    private final OrderInfoService orderInfoService;
    private final PaymentInfoService paymentInfoService;
    private final PaymentOrderService paymentOrderService;
    private final PaymentSuccessService paymentSuccessService;
    private final WxPayHttpClient wxPayHttpClient;
    private final WxPayNotificationDecoder wxPayNotificationDecoder;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;

    public WxPayOrderService(PaymentConfigLoader paymentConfigLoader,
                             OrderInfoService orderInfoService,
                             PaymentInfoService paymentInfoService,
                             PaymentOrderService paymentOrderService,
                             PaymentSuccessService paymentSuccessService,
                             WxPayHttpClient wxPayHttpClient,
                             WxPayNotificationDecoder wxPayNotificationDecoder,
                             DistributedLockTemplate distributedLockTemplate,
                             TransactionTemplate transactionTemplate) {
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderInfoService = orderInfoService;
        this.paymentInfoService = paymentInfoService;
        this.paymentOrderService = paymentOrderService;
        this.paymentSuccessService = paymentSuccessService;
        this.wxPayHttpClient = wxPayHttpClient;
        this.wxPayNotificationDecoder = wxPayNotificationDecoder;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public WxPayNativeVO nativePay(Long productId) {
        return nativePay(productId, null);
    }

    @Override
    public WxPayNativeVO nativePay(Long productId, Long paymentAppId) {
        return distributedLockTemplate.execute(
                "payment:wx:native:v3:" + productId + ":" + (paymentAppId == null ? "default" : paymentAppId),
                3000L,
                -1L,
                () -> doNativePay(productId, paymentAppId)
        );
    }

    private WxPayNativeVO doNativePay(Long productId, Long paymentAppId) {
        PaymentAppConfig payConfig = resolveWxPayConfig(paymentAppId);
        OrderInfo orderInfo = orderInfoService.createOrReuseOrder(
                productId,
                PayType.WXPAY.getType(),
                payConfig.getAppId(),
                PaymentConfigLoader.CHANNEL_WXPAY
        );
        if (orderInfo == null) {
            throw new BizException("订单创建失败");
        }
        if (StringUtils.hasText(orderInfo.getCodeUrl())) {
            log.info("订单已存在，复用二维码，orderNo={}", orderInfo.getOrderNo());
            // 复用二维码也确保存在活跃支付单（兼容历史订单）。
            paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_WXPAY);
            return buildNativePayResult(orderInfo.getOrderNo(), orderInfo.getCodeUrl());
        }

        // V2：发起支付前创建支付单（临近过期拒绝，过期时间取 min(order.expire, now+2h)）。
        cc.ivera.entity.PaymentOrder paymentOrder = paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_WXPAY);
        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("appid", required(payConfig.getAppid(), "微信appid未配置"));
        paramsMap.put("mchid", required(payConfig.getMchId(), "微信商户号未配置"));
        paramsMap.put("description", orderInfo.getTitle());
        paramsMap.put("out_trade_no", orderInfo.getOrderNo());
        paramsMap.put("notify_url", buildNotifyUrl(payConfig, WxNotifyType.NATIVE_NOTIFY));

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("total", orderInfo.getTotalFee());
        amountMap.put("currency", "CNY");
        paramsMap.put("amount", amountMap);

        String jsonParams = JsonUtils.toJson(paramsMap);
        log.info("微信V3 Native下单请求参数 ===> {}", jsonParams);

        String url = required(payConfig.getDomain(), "微信支付网关domain未配置").concat(WxApiType.NATIVE_PAY.getType());
        try {
            String bodyAsString = wxPayHttpClient.postJson(payConfig, url, jsonParams, "Native下单失败");
            Map<String, Object> resultMap = JsonUtils.toObjectMap(bodyAsString);
            String codeUrl = getString(resultMap, "code_url");
            if (!StringUtils.hasText(codeUrl)) {
                throw new BizException("微信Native下单响应缺少code_url");
            }
            orderInfoService.saveCodeUrl(orderInfo.getOrderNo(), codeUrl);
            paymentOrderService.markPaying(paymentOrder.getPaymentNo(), codeUrl);
            return buildNativePayResult(orderInfo.getOrderNo(), codeUrl);
        } catch (IOException e) {
            throw new BizException("Native下单失败", e);
        }
    }

    @Override
    public WxPayNativeVO nativePayByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("订单号不能为空");
        }
        return distributedLockTemplate.execute("payment:wx:native:v3:order:" + orderNo, 3000L, -1L, () -> {
            OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
            if (orderInfo == null) {
                throw new BizException("订单不存在，orderNo=" + orderNo);
            }
            if (!PayType.WXPAY.getType().equals(orderInfo.getPaymentType())) {
                throw new BizException("订单支付方式不是微信，orderNo=" + orderNo);
            }
            if (!OrderStatus.NOTPAY.getType().equals(orderInfo.getLegacyStatus())) {
                throw new BizException("订单当前状态不可支付：" + orderInfo.getLegacyStatus());
            }
            // V2：确保存在活跃支付单（临近过期在此拒绝）。
            cc.ivera.entity.PaymentOrder paymentOrder = paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_WXPAY);
            if (StringUtils.hasText(orderInfo.getCodeUrl())) {
                return buildNativePayResult(orderInfo.getOrderNo(), orderInfo.getCodeUrl());
            }
            PaymentAppConfig payConfig = resolveWxPayConfigByOrderNo(orderNo);
            Map<String, Object> paramsMap = new HashMap<>();
            paramsMap.put("appid", required(payConfig.getAppid(), "微信appid未配置"));
            paramsMap.put("mchid", required(payConfig.getMchId(), "微信商户号未配置"));
            paramsMap.put("description", orderInfo.getTitle());
            paramsMap.put("out_trade_no", orderInfo.getOrderNo());
            paramsMap.put("notify_url", buildNotifyUrl(payConfig, WxNotifyType.NATIVE_NOTIFY));
            Map<String, Object> amountMap = new HashMap<>();
            amountMap.put("total", orderInfo.getTotalFee());
            amountMap.put("currency", "CNY");
            paramsMap.put("amount", amountMap);
            String jsonParams = JsonUtils.toJson(paramsMap);
            String url = required(payConfig.getDomain(), "微信支付网关domain未配置").concat(WxApiType.NATIVE_PAY.getType());
            try {
                String bodyAsString = wxPayHttpClient.postJson(payConfig, url, jsonParams, "Native下单失败");
                Map<String, Object> resultMap = JsonUtils.toObjectMap(bodyAsString);
                String codeUrl = getString(resultMap, "code_url");
                if (!StringUtils.hasText(codeUrl)) {
                    throw new BizException("微信Native下单响应缺少code_url");
                }
                orderInfoService.saveCodeUrl(orderInfo.getOrderNo(), codeUrl);
                paymentOrderService.markPaying(paymentOrder.getPaymentNo(), codeUrl);
                return buildNativePayResult(orderInfo.getOrderNo(), codeUrl);
            } catch (IOException e) {
                throw new BizException("Native下单失败", e);
            }
        });
    }

    @Override
    public void processOrder(Map<String, Object> bodyMap) {
        log.info("处理微信支付结果通知");

        String plainText;
        try {
            plainText = wxPayNotificationDecoder.decryptResource(bodyMap);
        } catch (GeneralSecurityException e) {
            throw new BizException("微信支付通知解密失败", e);
        }

        Map<String, Object> plainTextMap = JsonUtils.toObjectMap(plainText);
        String orderNo = getString(plainTextMap, "out_trade_no");
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("微信支付通知缺少商户订单号");
        }

        String notifyId = getString(bodyMap, "id");
        distributedLockTemplate.execute("payment:wx:notify:pay:" + orderNo, PAY_NOTIFY_LOCK_WAIT_MS, -1L, () ->
                transactionTemplate.execute(status -> {
                    doProcessOrderNotifyInTransaction(orderNo, plainTextMap, plainText, notifyId);
                    return null;
                })
        );
    }

    private void doProcessOrderNotifyInTransaction(String orderNo,
                                                   Map<String, Object> plainTextMap,
                                                   String plainText,
                                                   String notifyId) {
        OrderInfo lockedOrder = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
        if (lockedOrder == null) {
            throw new BizException("微信支付通知对应订单不存在，orderNo=" + orderNo);
        }

        validateWxPayOrderNotify(lockedOrder, plainTextMap);

        // V2：统一支付成功处理器（支付单状态机 + 订单 CAS + 预占提交 + 异常冲正）。
        String transactionId = getString(plainTextMap, "transaction_id");
        Integer payerTotal = getWxPayTotalAmount(plainTextMap);
        boolean firstSettled = paymentSuccessService.handlePaymentSuccess(
                orderNo, PaymentConfigLoader.CHANNEL_WXPAY, transactionId, payerTotal);
        if (firstSettled) {
            paymentInfoService.createPaymentInfo(plainText);
            log.info("微信支付通知处理完成，orderNo={}, notifyId={}", orderNo, notifyId);
        } else {
            log.info("微信支付通知幂等/冲正路径，orderNo={}, notifyId={}", orderNo, notifyId);
        }
    }

    @Override
    public void cancelOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("订单号不能为空");
        }
        distributedLockTemplate.execute("payment:order:cancel:" + orderNo, 3000L, -1L, () ->
                transactionTemplate.execute(status -> {
                    OrderInfo lockedOrder = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
                    if (lockedOrder == null) {
                        throw new BizException("订单不存在，orderNo=" + orderNo);
                    }
                    if (!OrderStatus.NOTPAY.getType().equals(lockedOrder.getLegacyStatus())) {
                        log.info("订单当前状态不允许取消，orderNo={}, currentStatus={}", orderNo, lockedOrder.getLegacyStatus());
                        return null;
                    }
                    closeOrder(orderNo);
                    orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CANCEL);
                    return null;
                })
        );
    }

    @Override
    public String queryOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("订单号不能为空");
        }
        try {
            return queryChannel(orderNo);
        } catch (IOException e) {
            throw new BizException("查单接口调用异常", e);
        }
    }

    /**
     * 对账用查单：渠道明确返回 404 ORDER_NOT_EXIST（渠道侧从无此交易，即从未发起支付）时返回 null，
     * 调用方可据此走本地关单；网络/5xx 等不确定错误仍抛 BizException，保持本地状态等待下轮重试，避免单边账。
     */
    private String queryOrderBodyIfExists(String orderNo) {
        try {
            return queryChannel(orderNo);
        } catch (IOException e) {
            if (isOrderNotExistOnChannel(e)) {
                log.info("微信查单渠道无此交易（从未发起支付），orderNo={}", orderNo);
                return null;
            }
            throw new BizException("查单接口调用异常", e);
        }
    }

    private String queryChannel(String orderNo) throws IOException {
        PaymentAppConfig payConfig = resolveWxPayConfigByOrderNo(orderNo);
        String url = String.format(WxApiType.ORDER_QUERY_BY_NO.getType(), orderNo);
        url = required(payConfig.getDomain(), "微信支付网关domain未配置")
                .concat(url)
                .concat("?mchid=")
                .concat(required(payConfig.getMchId(), "微信商户号未配置"));
        return wxPayHttpClient.get(payConfig, url, "查单接口调用异常");
    }

    private boolean isOrderNotExistOnChannel(IOException e) {
        String message = e.getMessage();
        return message != null && message.contains(WX_ERR_CODE_ORDER_NOT_EXIST);
    }

    @Override
    public WxPayStatusVO queryPaymentStatus(String orderNo) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        if (orderInfo == null) {
            throw new BizException("订单不存在，orderNo=" + orderNo);
        }
        if (!PayType.WXPAY.getType().equals(orderInfo.getPaymentType())) {
            throw new BizException("订单不是微信支付订单，orderNo=" + orderNo);
        }

        String localStatusBefore = orderInfo.getLegacyStatus();

        String result = queryOrderBodyIfExists(orderNo);
        Map<String, Object> resultMap;
        String tradeState;
        String tradeStateDesc;
        if (result == null) {
            // 渠道无此交易（从未发起支付）：等价 NOTPAY 展示，本地状态保持，不触发关单（关单由超时对账负责）。
            resultMap = new HashMap<>();
            resultMap.put("code", WX_ERR_CODE_ORDER_NOT_EXIST);
            tradeState = WxTradeState.NOTPAY.getType();
            tradeStateDesc = "渠道暂无交易（未发起支付）";
            resultMap.put("trade_state", tradeState);
            resultMap.put("trade_state_desc", tradeStateDesc);
            log.info("微信查单渠道无此交易，本地状态保持，orderNo={}", orderNo);
        } else {
            resultMap = JsonUtils.toObjectMap(result);
            tradeState = getString(resultMap, "trade_state");
            tradeStateDesc = getString(resultMap, "trade_state_desc");
            Map<String, Object> finalResultMap = resultMap;
            String finalResult = result;
            String finalTradeState = tradeState;
            distributedLockTemplate.execute("payment:wx:query:order:" + orderNo, 5000L, -1L, () ->
                    transactionTemplate.execute(status -> {
                        doSyncOrderStatusFromWxQuery(orderNo, finalResultMap, finalResult, finalTradeState, false);
                        return null;
                    })
            );
        }

        String localStatusAfter = orderInfoService.getOrderStatus(orderNo);
        return buildPaymentStatusResult(orderNo, tradeState, tradeStateDesc, localStatusBefore, localStatusAfter, resultMap);
    }

    @Override
    public void checkOrderStatus(String orderNo) {
        String result = queryOrderBodyIfExists(orderNo);
        distributedLockTemplate.execute("payment:wx:check:order:" + orderNo, 5000L, -1L, () ->
                transactionTemplate.execute(status -> {
                    if (result == null) {
                        closeLocalOrderWhenChannelAbsent(orderNo);
                    } else {
                        Map<String, Object> resultMap = JsonUtils.toObjectMap(result);
                        String tradeState = getString(resultMap, "trade_state");
                        doSyncOrderStatusFromWxQuery(orderNo, resultMap, result, tradeState, true);
                    }
                    return null;
                })
        );
    }

    /**
     * 渠道查无此交易（从未发起支付）时的本地关单：渠道无单可关，直接走 V2 本地关单链路
     * （CAS NOTPAY->CLOSED：关闭活跃支付单 + 释放预占库存）。
     */
    private void closeLocalOrderWhenChannelAbsent(String orderNo) {
        OrderInfo lockedOrder = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
        if (lockedOrder == null) {
            throw new BizException("查单同步对应订单不存在，orderNo=" + orderNo);
        }
        if (!OrderStatus.NOTPAY.getType().equals(lockedOrder.getLegacyStatus())) {
            log.info("微信渠道无此交易但订单本地已处理，orderNo={}, currentStatus={}", orderNo, lockedOrder.getLegacyStatus());
            return;
        }
        log.warn("微信渠道查无此交易（从未发起支付），本地订单超时关单 ===> {}", orderNo);
        orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
    }

    private void doSyncOrderStatusFromWxQuery(String orderNo,
                                              Map<String, Object> resultMap,
                                              String result,
                                              String tradeState,
                                              boolean closeUnpaidOrder) {
        OrderInfo lockedOrder = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
        if (lockedOrder == null) {
            throw new BizException("查单同步对应订单不存在，orderNo=" + orderNo);
        }
        if (!OrderStatus.NOTPAY.getType().equals(lockedOrder.getLegacyStatus())) {
            log.info("微信查单同步发现订单已处理，orderNo={}, currentStatus={}", orderNo, lockedOrder.getLegacyStatus());
            return;
        }

        if (WxTradeState.SUCCESS.getType().equals(tradeState)) {
            validateWxPayOrderNotify(lockedOrder, resultMap);
            // 查单确认支付成功统一走支付成功处理器：支付单回写 SUCCESS/渠道交易号/实付金额/支付时间
            // + 成交收口其它渠道活跃支付单，与支付宝查单路径（queryAndSyncStatus/checkOrderStatus）对齐。
            String transactionId = getString(resultMap, "transaction_id");
            Integer payerTotal = getWxPayTotalAmount(resultMap);
            boolean firstSettled = paymentSuccessService.handlePaymentSuccess(
                    orderNo, PaymentConfigLoader.CHANNEL_WXPAY, transactionId, payerTotal);
            if (firstSettled) {
                paymentInfoService.createPaymentInfo(result);
            }
            return;
        }
        if (WxTradeState.NOTPAY.getType().equals(tradeState) && closeUnpaidOrder) {
            closeOrder(orderNo);
            orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
            return;
        }
        if (WxTradeState.CLOSED.getType().equals(tradeState)) {
            orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.CLOSED);
        }
    }

    @Override
    public WxPayNativeVO nativePayV2(Long productId, String remoteAddr) {
        return nativePayV2(productId, remoteAddr, null);
    }

    @Override
    public WxPayNativeVO nativePayV2(Long productId, String remoteAddr, Long paymentAppId) {
        return distributedLockTemplate.execute(
                "payment:wx:native:v2:" + productId + ":" + (paymentAppId == null ? "default" : paymentAppId),
                3000L,
                -1L,
                () -> doNativePayV2(productId, remoteAddr, paymentAppId)
        );
    }

    private WxPayNativeVO doNativePayV2(Long productId, String remoteAddr, Long paymentAppId) {
        PaymentAppConfig payConfig = resolveWxPayConfig(paymentAppId);
        OrderInfo orderInfo = orderInfoService.createOrReuseOrder(
                productId,
                PayType.WXPAY.getType(),
                payConfig.getAppId(),
                PaymentConfigLoader.CHANNEL_WXPAY
        );
        if (orderInfo == null) {
            throw new BizException("订单创建失败");
        }
        if (StringUtils.hasText(orderInfo.getCodeUrl())) {
            paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_WXPAY);
            return buildNativePayResult(orderInfo.getOrderNo(), orderInfo.getCodeUrl());
        }
        // V2：发起支付前创建支付单（临近过期拒绝）。
        cc.ivera.entity.PaymentOrder paymentOrder = paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_WXPAY);

        HttpClientUtils client = new HttpClientUtils(required(payConfig.getDomain(), "微信支付网关domain未配置").concat(WxApiType.NATIVE_PAY_V2.getType()));
        Map<String, String> params = new HashMap<>();
        params.put("appid", required(payConfig.getAppid(), "微信appid未配置"));
        params.put("mch_id", required(payConfig.getMchId(), "微信商户号未配置"));
        params.put("nonce_str", WXPayUtil.generateNonceStr());
        params.put("body", orderInfo.getTitle());
        params.put("out_trade_no", orderInfo.getOrderNo());
        params.put("total_fee", String.valueOf(orderInfo.getTotalFee()));
        params.put("spbill_create_ip", StringUtils.hasText(remoteAddr) ? remoteAddr : "192.168.1.200");
        params.put("notify_url", buildNotifyUrl(payConfig, WxNotifyType.NATIVE_NOTIFY_V2));
        params.put("trade_type", "NATIVE");

        try {
            String xmlParams = WXPayUtil.generateSignedXml(params, required(payConfig.getPartnerKey(), "微信APIv2密钥partnerKey未配置"));
            client.setXmlParam(xmlParams);
            client.setHttps(true);
            client.post();
            String resultXml = client.getContent();
            Map<String, String> resultMap = WXPayUtil.xmlToMap(resultXml);
            if ("FAIL".equals(resultMap.get("return_code")) || "FAIL".equals(resultMap.get("result_code"))) {
                throw new BizException("微信支付V2统一下单错误：" + resultXml);
            }
            String codeUrl = resultMap.get("code_url");
            if (!StringUtils.hasText(codeUrl)) {
                throw new BizException("微信支付V2统一下单响应缺少code_url");
            }
            orderInfoService.saveCodeUrl(orderInfo.getOrderNo(), codeUrl);
            return buildNativePayResult(orderInfo.getOrderNo(), codeUrl);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信支付V2统一下单失败", e);
        }
    }

    @Override
    public WxPayJsapiVO jsapiPay(OrderInfo orderInfo, String openid) {
        PaymentAppConfig payConfig = resolveWxPayConfig(orderInfo == null ? null : orderInfo.getPaymentAppId());
        String lockKey = "payment:wx:jsapi:" + (orderInfo == null ? "unknown" : orderInfo.getOrderNo());
        return distributedLockTemplate.execute(lockKey, 5000L, -1L, () -> {
            // V2：JSAPI 每次发起都是一次新的渠道支付尝试，创建独立支付单。
            cc.ivera.entity.PaymentOrder paymentOrder = paymentOrderService.startPayment(orderInfo, PaymentConfigLoader.CHANNEL_WXPAY);
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectMapper objectMapper = new ObjectMapper();
                ObjectNode rootNode = objectMapper.createObjectNode();
                rootNode.put("mchid", required(payConfig.getMchId(), "微信商户号未配置"))
                        .put("appid", required(payConfig.getAppid(), "微信appid未配置"))
                        .put("description", orderInfo.getTitle())
                        .put("notify_url", buildNotifyUrl(payConfig, WxNotifyType.NATIVE_NOTIFY))
                        .put("out_trade_no", orderInfo.getOrderNo());
                rootNode.putObject("amount").put("total", orderInfo.getTotalFee());
                rootNode.putObject("payer").put("openid", openid);
                objectMapper.writeValue(bos, rootNode);

                String url = required(payConfig.getDomain(), "微信支付网关domain未配置").concat(WxApiType.JSAPI_PAY.getType());
                String bodyAsString = wxPayHttpClient.postJson(payConfig, url, bos.toString("UTF-8"), "JSAPI下单失败");
                Map<String, Object> responseMap = JsonUtils.toObjectMap(bodyAsString);
                String prepayId = (String) responseMap.get("prepay_id");
                paymentOrderService.markPaying(paymentOrder.getPaymentNo(), null);

                return getPayment("prepay_id=" + prepayId,
                        required(payConfig.getAppid(), "微信appid未配置"),
                        WxPayPrivateKeyUtil.load(required(payConfig.getPrivateKey(), "微信商户私钥内容未配置")));
            } catch (Exception e) {
                throw new BizException("支付失败" + e.getMessage(), e);
            }
        });
    }

    private void closeOrder(String orderNo) {
        PaymentAppConfig payConfig = resolveWxPayConfigByOrderNo(orderNo);
        String url = String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(), orderNo);
        url = required(payConfig.getDomain(), "微信支付网关domain未配置").concat(url);

        Map<String, String> paramsMap = new HashMap<>();
        paramsMap.put("mchid", required(payConfig.getMchId(), "微信商户号未配置"));
        try {
            wxPayHttpClient.postJson(payConfig, url, JsonUtils.toJson(paramsMap), "Native关单失败");
        } catch (IOException e) {
            if (isOrderNotExistOnChannel(e)) {
                // 渠道侧已无此交易（从未发起支付或已被渠道清理），无单可关，本地关单可继续。
                log.info("微信关单时渠道无此交易，无单可关视为关单完成，orderNo={}", orderNo);
                return;
            }
            throw new BizException("Native关单失败", e);
        }
    }

    private void validateWxPayOrderNotify(OrderInfo orderInfo, Map<String, Object> notifyMap) {
        if (!PayType.WXPAY.getType().equals(orderInfo.getPaymentType())) {
            throw new BizException("支付通知支付类型不匹配，orderNo=" + orderInfo.getOrderNo());
        }
        PaymentAppConfig payConfig = resolveWxPayConfig(orderInfo.getPaymentAppId());
        String notifyMchId = getString(notifyMap, "mchid");
        if (StringUtils.hasText(notifyMchId) && !notifyMchId.equals(payConfig.getMchId())) {
            throw new BizException("支付通知商户号不匹配，orderNo=" + orderInfo.getOrderNo());
        }
        String notifyAppid = getString(notifyMap, "appid");
        if (StringUtils.hasText(notifyAppid) && !notifyAppid.equals(payConfig.getAppid())) {
            throw new BizException("支付通知appid不匹配，orderNo=" + orderInfo.getOrderNo());
        }

        Integer notifyTotal = getWxPayTotalAmount(notifyMap);
        if (notifyTotal == null) {
            log.warn("微信支付通知缺少金额字段，跳过金额校验，orderNo={}", orderInfo.getOrderNo());
            return;
        }
        if (!notifyTotal.equals(orderInfo.getTotalFee())) {
            throw new BizException("支付通知金额与订单金额不一致，orderNo=" + orderInfo.getOrderNo());
        }
    }

    private Integer getWxPayTotalAmount(Map<String, Object> notifyMap) {
        Map<String, Object> amountMap = JsonUtils.toObjectMap(notifyMap == null ? null : notifyMap.get("amount"));
        if (amountMap == null) {
            return null;
        }
        Object total = amountMap.get("total");
        if (total == null) {
            total = amountMap.get("payer_total");
        }
        return toInteger(total);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private WxPayNativeVO buildNativePayResult(String orderNo, String codeUrl) {
        WxPayNativeVO vo = new WxPayNativeVO();
        vo.setCodeUrl(codeUrl);
        vo.setOrderNo(orderNo);
        return vo;
    }

    private WxPayStatusVO buildPaymentStatusResult(String orderNo,
                                                         String tradeState,
                                                         String tradeStateDesc,
                                                         String localStatusBefore,
                                                         String localStatusAfter,
                                                         Map<String, Object> wxPayResult) {
        WxPayStatusVO vo = new WxPayStatusVO();
        vo.setOrderNo(orderNo);
        vo.setTradeState(tradeState);
        vo.setTradeStateDesc(tradeStateDesc);
        vo.setLocalStatusBefore(localStatusBefore);
        vo.setLocalStatus(localStatusAfter);
        vo.setWxPayResult(wxPayResult);
        return vo;
    }

    private WxPayJsapiVO getPayment(String prepayId, String appId, PrivateKey privateKey) {
        WxPayJsapiVO vo = new WxPayJsapiVO();
        String nonceStr = UUID.randomUUID().toString().toUpperCase();
        long timeStamp = System.currentTimeMillis() / 1000;
        String source = appId + "\n" + timeStamp + "\n" + nonceStr + "\n" + prepayId + "\n";
        String sign = getSign(source.getBytes(StandardCharsets.UTF_8), privateKey);
        vo.setAppId(appId);
        vo.setTimeStamp(timeStamp);
        vo.setNonceStr(nonceStr);
        vo.setPackageValue(prepayId);
        vo.setSignType("RSA");
        vo.setPaySign(sign);
        return vo;
    }

    private String getSign(byte[] message, PrivateKey privateKey) {
        try {
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(privateKey);
            sign.update(message);
            return Base64.getEncoder().encodeToString(sign.sign());
        } catch (Exception e) {
            throw new BizException("获取微信支付签名失败", e);
        }
    }

    private PaymentAppConfig resolveWxPayConfig(Long paymentAppId) {
        PaymentAppConfig config = paymentAppId == null
                ? paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigLoader.CHANNEL_WXPAY)
                : paymentConfigLoader.getRequiredAppConfig(paymentAppId);
        if (config == null) {
            throw new BizException("微信支付渠道未配置或未启用");
        }
        if (!PaymentConfigLoader.CHANNEL_WXPAY.equals(config.getChannelCode())) {
            throw new BizException("支付应用不是微信支付渠道");
        }
        return config;
    }

    private PaymentAppConfig resolveWxPayConfigByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        return resolveWxPayConfig(orderInfo == null ? null : orderInfo.getPaymentAppId());
    }

    private String buildNotifyUrl(PaymentAppConfig config, WxNotifyType notifyType) {
        return required(config.getNotifyUrl(), "微信支付通知域名notifyUrl未配置").concat(notifyType.getType());
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
