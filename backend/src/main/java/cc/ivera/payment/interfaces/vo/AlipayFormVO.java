package cc.ivera.payment.interfaces.vo;

import lombok.Data;

/**
 * 支付宝统一收单下单页面响应 VO（返回自动提交表单 HTML）。
 */
@Data
public class AlipayFormVO {

    private String formStr;
}
