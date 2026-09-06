package cc.ivera.service;

import java.util.Map;

public interface PaymentInfoService {

    void createPaymentInfo(String plainText);

    void createPaymentInfoForWxPayV2(Map<String, String> params, String content);

    void createPaymentInfoForAliPay(Map<String, ?> params);
}
