package cc.ivera.payment.interfaces;

import cc.ivera.payment.application.PaymentChannelService;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.interfaces.dto.PaymentChannelRequest;
import cc.ivera.payment.interfaces.dto.PaymentStatusRequest;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/payment-channel")
@Api(tags = "支付渠道配置管理")
@Validated
public class PaymentChannelController {

    private final PaymentChannelService paymentChannelService;
    private final PaymentConfigGateway paymentConfigLoader;

    public PaymentChannelController(PaymentChannelService paymentChannelService,
                                    PaymentConfigGateway paymentConfigLoader) {
        this.paymentChannelService = paymentChannelService;
        this.paymentConfigLoader = paymentConfigLoader;
    }

    @ApiOperation("获取全部支付渠道配置")
    @GetMapping("/list-all")
    public R<List<PaymentChannel>> listAll() {
        return R.ok(paymentChannelService.listAllChannels());
    }

    @ApiOperation("获取启用支付渠道配置")
    @GetMapping("/list")
    public R<List<PaymentChannel>> listEnabled() {
        return R.ok(paymentChannelService.listEnabledChannels());
    }

    @ApiOperation("获取支付渠道详情")
    @GetMapping("/{id}")
    public R<PaymentChannel> getById(@PathVariable @Positive(message = "渠道ID必须大于0") Long id) {
        PaymentChannel channel = paymentChannelService.getById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付渠道不存在");
        }
        return R.ok(channel);
    }

    @ApiOperation("新增支付渠道配置")
    @PostMapping
    public R<PaymentChannel> create(@Valid @RequestBody PaymentChannelRequest request) {
        PaymentChannel channel = paymentChannelService.createChannel(request);
        paymentConfigLoader.reloadConfigs();
        return R.ok(channel).setMessage("支付渠道新增成功");
    }

    @ApiOperation("修改支付渠道配置")
    @PutMapping("/{id}")
    public R<PaymentChannel> update(@PathVariable @Positive(message = "渠道ID必须大于0") Long id,
                                    @Valid @RequestBody PaymentChannelRequest request) {
        PaymentChannel channel = paymentChannelService.updateChannel(id, request);
        paymentConfigLoader.reloadConfigs();
        return R.ok(channel).setMessage("支付渠道修改成功");
    }

    @ApiOperation("修改支付渠道状态")
    @PatchMapping("/{id}/status")
    public R<PaymentChannel> updateStatus(@PathVariable @Positive(message = "渠道ID必须大于0") Long id,
                                          @Valid @RequestBody PaymentStatusRequest request) {
        PaymentChannel channel = paymentChannelService.updateChannelStatus(id, request.getStatus());
        paymentConfigLoader.reloadConfigs();
        return R.ok(channel).setMessage("支付渠道状态修改成功");
    }

    @ApiOperation("删除支付渠道配置")
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable @Positive(message = "渠道ID必须大于0") Long id) {
        paymentChannelService.deleteChannel(id);
        paymentConfigLoader.reloadConfigs();
        return R.ok().setMessage("支付渠道删除成功");
    }
}
