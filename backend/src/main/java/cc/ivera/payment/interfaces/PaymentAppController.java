package cc.ivera.payment.interfaces;

import cc.ivera.payment.application.PaymentAppService;
import cc.ivera.payment.application.PaymentChannelService;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentApp;
import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.interfaces.dto.PaymentAppRequest;
import cc.ivera.payment.interfaces.dto.PaymentStatusRequest;
import cc.ivera.payment.interfaces.vo.PaymentAppViewVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin
@RestController
@RequestMapping("/api/payment-app")
@Api(tags = "支付应用管理")
@Slf4j
@Validated
public class PaymentAppController {

    private final PaymentAppService paymentAppService;
    private final PaymentChannelService paymentChannelService;
    private final PaymentConfigGateway paymentConfigLoader;

    public PaymentAppController(PaymentAppService paymentAppService,
                                PaymentChannelService paymentChannelService,
                                PaymentConfigGateway paymentConfigLoader) {
        this.paymentAppService = paymentAppService;
        this.paymentChannelService = paymentChannelService;
        this.paymentConfigLoader = paymentConfigLoader;
    }

    @ApiOperation("获取所有启用的支付应用列表（带渠道信息，前端支付页使用）")
    @GetMapping("/list")
    public R<List<PaymentAppViewVO>> listEnabledApps() {
        log.info("获取启用支付应用列表");
        return R.ok(buildAppViewList(paymentAppService.listEnabledApps(), true));
    }

    @ApiOperation("获取全部支付应用列表（配置管理页使用）")
    @GetMapping("/list-all")
    public R<List<PaymentAppViewVO>> listAllApps() {
        log.info("获取全部支付应用列表");
        return R.ok(buildAppViewList(paymentAppService.listAllApps(), false));
    }

    @ApiOperation("根据渠道ID获取启用的支付应用列表")
    @GetMapping("/list-by-channel/{channelId}")
    public R<List<PaymentApp>> listByChannelId(@PathVariable @Positive(message = "渠道ID必须大于0") Long channelId) {
        log.info("根据渠道ID获取支付应用列表，channelId={}", channelId);
        return R.ok(paymentAppService.listEnabledAppsByChannelId(channelId));
    }

    @ApiOperation("获取支付应用详情")
    @GetMapping("/{id}")
    public R<PaymentApp> getById(@PathVariable @Positive(message = "应用ID必须大于0") Long id) {
        log.info("获取支付应用详情，id={}", id);
        PaymentApp app = paymentAppService.getById(id);
        if (app == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付应用不存在");
        }
        return R.ok(app);
    }

    @ApiOperation("新增支付应用")
    @PostMapping
    public R<PaymentApp> create(@Valid @RequestBody PaymentAppRequest request) {
        PaymentApp app = paymentAppService.createApp(request);
        paymentConfigLoader.reloadConfigs();
        return R.ok(app).setMessage("支付应用新增成功");
    }

    @ApiOperation("修改支付应用")
    @PutMapping("/{id}")
    public R<PaymentApp> update(@PathVariable @Positive(message = "应用ID必须大于0") Long id,
                                @Valid @RequestBody PaymentAppRequest request) {
        PaymentApp app = paymentAppService.updateApp(id, request);
        paymentConfigLoader.reloadConfigs();
        return R.ok(app).setMessage("支付应用修改成功");
    }

    @ApiOperation("修改支付应用状态")
    @PatchMapping("/{id}/status")
    public R<PaymentApp> updateStatus(@PathVariable @Positive(message = "应用ID必须大于0") Long id,
                                      @Valid @RequestBody PaymentStatusRequest request) {
        PaymentApp app = paymentAppService.updateAppStatus(id, request.getStatus());
        paymentConfigLoader.reloadConfigs();
        return R.ok(app).setMessage("支付应用状态修改成功");
    }

    @ApiOperation("删除支付应用")
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable @Positive(message = "应用ID必须大于0") Long id) {
        paymentAppService.deleteApp(id);
        paymentConfigLoader.reloadConfigs();
        return R.ok().setMessage("支付应用删除成功");
    }

    @ApiOperation("获取所有启用的支付渠道")
    @GetMapping("/channels")
    public R<List<PaymentChannel>> listEnabledChannels() {
        log.info("获取启用支付渠道列表");
        return R.ok(paymentChannelService.listEnabledChannels());
    }

    private List<PaymentAppViewVO> buildAppViewList(List<PaymentApp> apps, boolean onlyEnabledChannel) {
        List<PaymentChannel> channels = onlyEnabledChannel
            ? paymentChannelService.listEnabledChannels()
            : paymentChannelService.listAllChannels();
        Map<Long, PaymentChannel> channelMap = channels.stream()
            .collect(Collectors.toMap(PaymentChannel::getId, channel -> channel, (left, right) -> left));

        List<PaymentAppViewVO> result = new ArrayList<>();
        for (PaymentApp app : apps) {
            PaymentChannel channel = channelMap.get(app.getChannelId());
            if (onlyEnabledChannel && channel == null) {
                continue;
            }
            PaymentAppViewVO vo = new PaymentAppViewVO();
            vo.setId(app.getId());
            vo.setAppName(app.getAppName());
            vo.setAppCode(app.getAppCode());
            vo.setAppStatus(app.getAppStatus());
            vo.setChannelId(app.getChannelId());
            vo.setChannelCode(channel == null ? null : channel.getChannelCode());
            vo.setChannelName(channel == null ? null : channel.getChannelName());
            vo.setAppDesc(app.getAppDesc());
            vo.setSortOrder(app.getSortOrder());
            vo.setCreateTime(app.getCreateTime());
            vo.setUpdateTime(app.getUpdateTime());
            result.add(vo);
        }
        return result;
    }
}
