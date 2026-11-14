package cc.ivera.payment.application.impl.wxpay;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;
import cc.ivera.payment.application.wxpay.WxPayRefundFacade;
import cc.ivera.payment.domain.enums.ChannelRefundState;
import cc.ivera.payment.domain.enums.wxpay.WxApiType;
import cc.ivera.payment.domain.enums.wxpay.WxNotifyType;
import cc.ivera.payment.domain.enums.wxpay.WxRefundStatus;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.util.HttpClientUtils;
import cc.ivera.shared.infrastructure.util.JsonUtils;
import com.github.wxpay.sdk.WXPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

@Service
@Slf4j
public class WxPayRefundService implements WxPayRefundFacade {

    private static final Map<String, ChannelRefundState> WX_REFUND_STATE_MAP;

    static {
        Map<String, ChannelRefundState> m = new HashMap<>();
        m.put(WxRefundStatus.SUCCESS.getType(), ChannelRefundState.SUCCESS);
        m.put(WxRefundStatus.PROCESSING.getType(), ChannelRefundState.PROCESSING);
        m.put(WxRefundStatus.ABNORMAL.getType(), ChannelRefundState.ABNORMAL);
        m.put(WxRefundStatus.CLOSED.getType(), ChannelRefundState.CLOSED);
        m.put("REFUNDCLOSE", ChannelRefundState.CLOSED);
        m.put("CHANGE", ChannelRefundState.ABNORMAL);
        WX_REFUND_STATE_MAP = Collections.unmodifiableMap(m);
    }

    private final PaymentConfigGateway paymentConfigLoader;
    private final OrderPaymentApi orderInfoService;
    private final WxPayHttpClient wxPayHttpClient;
    private final WxPayNotificationDecoder wxPayNotificationDecoder;

    public WxPayRefundService(
        PaymentConfigGateway paymentConfigLoader,
        OrderPaymentApi orderInfoService,
        WxPayHttpClient wxPayHttpClient,
        WxPayNotificationDecoder wxPayNotificationDecoder
    ) {
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderInfoService = orderInfoService;
        this.wxPayHttpClient = wxPayHttpClient;
        this.wxPayNotificationDecoder = wxPayNotificationDecoder;
    }

    @Override
    public ChannelRefundResult executeRefund(ChannelRefundCommand command) {
        if (command == null) {
            throw new BizException("退款申请单不能为空");
        }

        log.info("调用微信退款API, refundNo = {}", command.getRefundNo());

        PaymentAppConfig payConfig = resolveWxPayConfigByOrderNo(command.getOrderNo());

        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("out_trade_no", command.getOrderNo());
        paramsMap.put("out_refund_no", command.getRefundNo());
        paramsMap.put("reason", command.getReason());
        paramsMap.put("notify_url", buildNotifyUrl(payConfig, WxNotifyType.REFUND_NOTIFY));

        Map<String, Object> amountMap = new HashMap<>();
        amountMap.put("refund", command.getRefundAmount());
        amountMap.put("total", command.getTotalFee());
        amountMap.put("currency", "CNY");
        paramsMap.put("amount", amountMap);

        String jsonParams = JsonUtils.toJson(paramsMap);
        log.info("微信退款请求参数 ===> {}", jsonParams);

        String url = required(payConfig.getDomain(), "微信支付网关domain未配置").concat(WxApiType.DOMESTIC_REFUNDS.getType());
        WxPayHttpClient.WxPayHttpResponse response;
        try {
            response = wxPayHttpClient.postJsonForResponse(payConfig, url, jsonParams);
        } catch (IOException e) {
            return ChannelRefundResult.failed(e.getMessage(), e.getMessage());
        }
        if (response.isSuccessful()) {
            log.info("微信退款申请已受理，返回结果 = {}", response.getBody());
            return ChannelRefundResult.processing(response.getBody());
        }

        String errorMessage = "微信退款异常，响应码 = " + response.getStatusCode() + ", 返回结果 = " + response.getBody();
        return ChannelRefundResult.failed(response.getBody(), errorMessage);
    }

    @Override
    public ChannelRefundStatus queryRefund(ChannelRefundQuery query) {
        if (query == null) {
            throw new BizException("退款查询参数不能为空");
        }
        log.info("调用微信退款查询接口 ===> {}", query.getRefundNo());
        PaymentAppConfig payConfig = resolveWxPayConfigByOrderNo(query.getOrderNo());

        String url = String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(), query.getRefundNo());
        url = required(payConfig.getDomain(), "微信支付网关domain未配置").concat(url);

        String result;
        try {
            result = wxPayHttpClient.get(payConfig, url, "查询微信退款异常");
        } catch (IOException e) {
            throw new BizException("查询微信退款异常", e);
        }

        Map<String, Object> resultMap = JsonUtils.toObjectMap(result);
        String channelStatus = getString(resultMap, "status");
        if (channelStatus == null || channelStatus.trim().isEmpty()) {
            log.warn("微信退款查询结果缺少status，refundNo={}", query.getRefundNo());
        }

        ChannelRefundStatus status = new ChannelRefundStatus();
        status.setChannelStatus(channelStatus);
        status.setState(mapWxRefundState(channelStatus));
        status.setRawBody(result);
        status.setChannelRefundId(getString(resultMap, "refund_id"));
        status.setTotalFee(getAmountInteger(resultMap, "total"));
        status.setRefundAmount(getAmountInteger(resultMap, "refund"));
        return status;
    }

    @Override
    public ChannelRefundSyncResult queryRefundStatusForSync(ChannelRefundQuery query) {
        ChannelRefundStatus status = queryRefund(query);
        if (status == null) {
            throw new BizException("微信退款查询无结果");
        }
        log.info("微信退款查询结果 ===> {}", status.getRawBody());
        return ChannelRefundSyncResult.of(
            query.getOrderNo(),
            query.getRefundNo(),
            status.getChannelRefundId(),
            status.getChannelStatus(),
            status.getState(),
            status.getRawBody(),
            status.getTotalFee(),
            status.getRefundAmount());
    }

    @Override
    public List<ChannelRefundSyncResult> queryOrderRefundsForSync(String orderNo, String channel) {
        log.info("调用微信订单退款查询接口 ===> {}", orderNo);

        List<ChannelRefundSyncResult> resultList = new ArrayList<>();
        int offset = 0;
        while (true) {
            Map<String, String> resultMap = queryOrderRefundsByV2(orderNo, offset);
            if (resultMap.isEmpty()) {
                return resultList;
            }

            int pageRefundCount = parseInteger(resultMap.get("refund_count"), 0);
            int totalRefundCount = parseInteger(resultMap.get("total_refund_count"), pageRefundCount);
            if (pageRefundCount <= 0) {
                return resultList;
            }

            int pageSize = 0;
            for (int i = 0; i < pageRefundCount; i++) {
                String refundNo = resultMap.get("out_refund_no_" + i);
                if (refundNo == null || refundNo.trim().isEmpty()) {
                    continue;
                }

                pageSize++;
                String channelStatus = resultMap.get("refund_status_" + i);
                Map<String, Object> item = new HashMap<>();
                item.put("out_trade_no", resultMap.get("out_trade_no"));
                item.put("out_refund_no", refundNo);
                item.put("refund_id", resultMap.get("refund_id_" + i));
                item.put("refund_status", channelStatus);
                item.put("total_fee", resultMap.get("total_fee"));
                item.put("refund_fee", resultMap.get("refund_fee_" + i));
                item.put("offset", offset);

                resultList.add(ChannelRefundSyncResult.of(
                    resultMap.get("out_trade_no"),
                    refundNo,
                    resultMap.get("refund_id_" + i),
                    channelStatus,
                    mapWxRefundState(channelStatus),
                    JsonUtils.toJson(item),
                    parseInteger(resultMap.get("total_fee"), null),
                    parseInteger(resultMap.get("refund_fee_" + i), null)));
            }

            if (pageSize == 0 || resultList.size() >= totalRefundCount) {
                return resultList;
            }
            offset += pageSize;
        }
    }

    @Override
    public ChannelRefundSyncResult processRefundNotify(Map<String, Object> bodyMap) {
        log.info("处理微信退款结果通知");

        String plainText;
        try {
            plainText = wxPayNotificationDecoder.decryptResource(bodyMap);
        } catch (GeneralSecurityException e) {
            throw new BizException("微信退款通知解密失败", e);
        }

        Map<String, Object> plainTextMap = JsonUtils.toObjectMap(plainText);
        String refundNo = getString(plainTextMap, "out_refund_no");
        if (!StringUtils.hasText(refundNo)) {
            throw new BizException("微信退款通知缺少商户退款单号");
        }

        String channelStatus = getString(plainTextMap, "refund_status");
        ChannelRefundSyncResult syncResult = buildChannelRefundSyncResult(plainTextMap, channelStatus, plainText);
        log.info("微信退款通知解析完成，refundNo={}, notifyId={}", refundNo, getString(bodyMap, "id"));
        return syncResult;
    }

    private ChannelRefundSyncResult buildChannelRefundSyncResult(Map<String, Object> refundMap,
                                                                 String channelStatus,
                                                                 String content) {
        return ChannelRefundSyncResult.of(
            getString(refundMap, "out_trade_no"),
            getString(refundMap, "out_refund_no"),
            getString(refundMap, "refund_id"),
            channelStatus,
            mapWxRefundState(channelStatus),
            content,
            getAmountInteger(refundMap, "total"),
            getAmountInteger(refundMap, "refund"));
    }

    private ChannelRefundState mapWxRefundState(String channelStatus) {
        return WX_REFUND_STATE_MAP.getOrDefault(channelStatus, ChannelRefundState.UNKNOWN);
    }

    private Map<String, String> queryOrderRefundsByV2(String orderNo, int offset) {
        PaymentAppConfig payConfig = resolveWxPayConfigByOrderNo(orderNo);
        HttpClientUtils client = new HttpClientUtils(required(payConfig.getDomain(), "微信支付网关domain未配置").concat(WxApiType.REFUND_QUERY_V2.getType()));
        Map<String, String> params = new HashMap<>();
        params.put("appid", required(payConfig.getAppid(), "微信appid未配置"));
        params.put("mch_id", required(payConfig.getMchId(), "微信商户号未配置"));
        params.put("nonce_str", WXPayUtil.generateNonceStr());
        params.put("out_trade_no", orderNo);
        params.put("offset", String.valueOf(offset));

        try {
            String xmlParams = WXPayUtil.generateSignedXml(params, required(payConfig.getPartnerKey(), "微信APIv2密钥partnerKey未配置"));
            client.setXmlParam(xmlParams);
            client.setHttps(true);
            client.post();

            String resultXml = client.getContent();
            log.info("微信订单退款查询结果 ===> {}", resultXml);
            Map<String, String> resultMap = WXPayUtil.xmlToMap(resultXml);
            if (!"SUCCESS".equals(resultMap.get("return_code"))) {
                throw new BizException("微信订单退款查询通信失败：" + resultMap.get("return_msg"));
            }
            if (!"SUCCESS".equals(resultMap.get("result_code"))) {
                if ("REFUNDNOTEXIST".equals(resultMap.get("err_code"))) {
                    return Collections.emptyMap();
                }
                throw new BizException("微信订单退款查询失败：" + resultMap.get("err_code_des"));
            }
            return resultMap;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("微信订单退款查询异常", e);
        }
    }

    private PaymentAppConfig resolveWxPayConfigByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        PaymentAppConfig config = orderInfo == null || orderInfo.getPaymentAppId() == null
            ? paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigGateway.CHANNEL_WXPAY)
            : paymentConfigLoader.getAppConfig(orderInfo.getPaymentAppId());
        if (config == null) {
            throw new BizException("微信支付渠道未配置或未启用");
        }
        if (!PaymentConfigGateway.CHANNEL_WXPAY.equals(config.getChannelCode())) {
            throw new BizException("订单绑定的支付应用不是微信支付渠道，orderNo=" + orderNo);
        }
        return config;
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

    private Integer getAmountInteger(Map<String, Object> map, String key) {
        Map<String, Object> amountMap = JsonUtils.toObjectMap(map == null ? null : map.get("amount"));
        return parseInteger(getString(amountMap, key), null);
    }

    private Integer parseInteger(String value, Integer defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
