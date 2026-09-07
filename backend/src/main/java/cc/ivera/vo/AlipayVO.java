package cc.ivera.vo;

import lombok.Data;

/**
 * 结账支付宝支付响应 VO（返回 form 表单 HTML）。
 */
@Data
public class AlipayVO {

    private String html;

    private String orderNo;
}
