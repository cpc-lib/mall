package cc.ivera.service.impl.wxpay;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.config.WxPayConfig;
import cc.ivera.enums.wxpay.WxApiType;
import cc.ivera.exception.BizException;
import cc.ivera.service.wxpay.WxPayBillFacade;
import cc.ivera.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

@Service
@Slf4j
public class WxPayBillService implements WxPayBillFacade {

    private static final ZoneId BILL_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String BILL_CATEGORY_TRADE = "tradebill";

    private static final String BILL_CATEGORY_FUND_FLOW = "fundflowbill";

    private final WxPayConfig wxPayConfig;

    private final PaymentConfigLoader paymentConfigLoader;

    private final WxPayHttpClient wxPayHttpClient;

    public WxPayBillService(
        WxPayConfig wxPayConfig,
        PaymentConfigLoader paymentConfigLoader,
        WxPayHttpClient wxPayHttpClient
    ) {
        this.wxPayConfig = wxPayConfig;
        this.paymentConfigLoader = paymentConfigLoader;
        this.wxPayHttpClient = wxPayHttpClient;
    }

    @Override
    public String queryBill(String billDate, String type, String billType, String accountType, String tarType) {
        log.warn("申请微信账单接口调用 billDate={}, type={}, billType={}, accountType={}, tarType={}",
                billDate, type, billType, accountType, tarType);

        validateBillDate(billDate);
        PaymentAppConfig payConfig = resolveWxPayConfig();
        String url = buildBillUrl(payConfig, billDate, type, billType, accountType, tarType);

        String bodyAsString;
        try {
            bodyAsString = wxPayHttpClient.get(payConfig, url, "申请微信账单异常");
        } catch (IOException e) {
            throw new BizException("申请微信账单异常", e);
        }

        Map<String, Object> resultMap = JsonUtils.toObjectMap(bodyAsString);
        String downloadUrl = getString(resultMap, "download_url");
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            throw new BizException("申请微信账单失败，响应缺少download_url");
        }
        return downloadUrl;
    }

    @Override
    public String downloadBill(String billDate, String type, String billType, String accountType, String tarType) {
        log.warn("下载微信账单接口调用 billDate={}, type={}, billType={}, accountType={}, tarType={}",
                billDate, type, billType, accountType, tarType);

        PaymentAppConfig payConfig = resolveWxPayConfig();
        String downloadUrl = queryBill(billDate, type, billType, accountType, tarType);
        try {
            return wxPayHttpClient.getNoSign(payConfig, downloadUrl, "下载微信账单异常");
        } catch (IOException e) {
            throw new BizException("下载微信账单异常", e);
        }
    }

    private String buildBillUrl(PaymentAppConfig payConfig, String billDate, String type, String billType, String accountType, String tarType) {
        try {
            URIBuilder builder = new URIBuilder(required(payConfig.getDomain(), "微信支付网关domain未配置").concat(resolveApiPath(type)));
            builder.addParameter("bill_date", billDate);
            if (BILL_CATEGORY_TRADE.equals(type)) {
                builder.addParameter("bill_type", defaultIfBlank(billType, "ALL"));
            } else if (BILL_CATEGORY_FUND_FLOW.equals(type)) {
                builder.addParameter("account_type", defaultIfBlank(accountType, "BASIC"));
            }
            if (tarType != null && !tarType.trim().isEmpty()) {
                builder.addParameter("tar_type", tarType.trim());
            }
            return builder.build().toString();
        } catch (URISyntaxException e) {
            throw new BizException("构造微信账单请求地址失败", e);
        }
    }

    private String resolveApiPath(String type) {
        if (BILL_CATEGORY_TRADE.equals(type)) {
            return WxApiType.TRADE_BILLS.getType();
        }
        if (BILL_CATEGORY_FUND_FLOW.equals(type)) {
            return WxApiType.FUND_FLOW_BILLS.getType();
        }
        throw new BizException("不支持的微信账单类型：" + type);
    }

    private void validateBillDate(String billDate) {
        LocalDate date;
        try {
            date = LocalDate.parse(billDate);
        } catch (DateTimeParseException e) {
            throw new BizException("账单日期格式必须为yyyy-MM-dd");
        }

        LocalDate today = LocalDate.now(BILL_ZONE);
        if (!date.isBefore(today)) {
            throw new BizException("微信账单只能申请历史日期，请使用" + today.minusDays(1) + "或更早日期");
        }
        if (date.isBefore(today.minusMonths(3))) {
            throw new BizException("微信账单只能申请近三个月内的账单");
        }
        if (date.equals(today.minusDays(1)) && LocalTime.now(BILL_ZONE).isBefore(LocalTime.of(10, 0))) {
            throw new BizException("微信昨日账单通常在10:00后生成，请稍后再试");
        }
    }


    private PaymentAppConfig resolveWxPayConfig() {
        PaymentAppConfig config = paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigLoader.CHANNEL_WXPAY);
        if (config != null) {
            return config;
        }
        PaymentAppConfig fallback = new PaymentAppConfig();
        fallback.setChannelCode(PaymentConfigLoader.CHANNEL_WXPAY);
        fallback.setAppid(wxPayConfig.getAppid());
        fallback.setMchId(wxPayConfig.getMchId());
        fallback.setMchSerialNo(wxPayConfig.getMchSerialNo());
        fallback.setPrivateKeyPath(wxPayConfig.getPrivateKeyPath());
        fallback.setApiV3Key(wxPayConfig.getApiV3Key());
        fallback.setPartnerKey(wxPayConfig.getPartnerKey());
        fallback.setDomain(wxPayConfig.getDomain());
        fallback.setNotifyUrl(wxPayConfig.getNotifyDomain());
        return fallback;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
