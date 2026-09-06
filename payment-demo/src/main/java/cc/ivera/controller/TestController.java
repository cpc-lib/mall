package cc.ivera.controller;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.vo.R;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "测试控制器")
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final PaymentConfigLoader paymentConfigLoader;

    public TestController(PaymentConfigLoader paymentConfigLoader) {
        this.paymentConfigLoader = paymentConfigLoader;
    }

    @GetMapping
    public R<String> getWxPayConfig() {
        PaymentAppConfig config = paymentConfigLoader.getDefaultAppConfigByChannelCode(PaymentConfigLoader.CHANNEL_WXPAY);
        return R.ok(config == null ? null : config.getMchId());
    }
}
