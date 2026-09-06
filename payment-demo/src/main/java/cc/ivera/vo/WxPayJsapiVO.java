package cc.ivera.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 微信 JSAPI 支付响应 VO（前端调起微信支付所需参数）。
 */
@Data
public class WxPayJsapiVO {

    private String appId;

    private Long timeStamp;

    private String nonceStr;

    @JsonProperty("package")
    private String packageValue;

    private String signType;

    private String paySign;
}
