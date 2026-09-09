package cc.ivera.payment.interfaces;

import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/payment-config")
@Api(tags = "支付配置加载管理")
public class PaymentConfigController {

    private final PaymentConfigGateway paymentConfigLoader;

    public PaymentConfigController(PaymentConfigGateway paymentConfigLoader) {
        this.paymentConfigLoader = paymentConfigLoader;
    }

    @ApiOperation("重新加载支付配置缓存")
    @PostMapping("/reload")
    public R<?> reload() {
        paymentConfigLoader.reloadConfigs();
        return R.ok().setMessage("支付配置已重新加载");
    }

    @ApiOperation("查看当前启用支付应用配置缓存")
    @GetMapping("/apps")
    public R<Map<Long, PaymentAppConfig>> apps() {
        return R.ok(paymentConfigLoader.getAllAppConfigs());
    }
}
