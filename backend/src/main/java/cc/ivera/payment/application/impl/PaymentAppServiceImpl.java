package cc.ivera.payment.application.impl;

import cc.ivera.payment.application.PaymentAppService;
import cc.ivera.payment.domain.model.PaymentApp;
import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.domain.repository.PaymentAppRepository;
import cc.ivera.payment.domain.repository.PaymentChannelRepository;
import cc.ivera.payment.interfaces.dto.PaymentAppRequest;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PaymentAppServiceImpl implements PaymentAppService {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private final PaymentAppRepository paymentAppRepository;
    private final PaymentChannelRepository paymentChannelRepository;

    public PaymentAppServiceImpl(PaymentAppRepository paymentAppRepository,
                                 PaymentChannelRepository paymentChannelRepository) {
        this.paymentAppRepository = paymentAppRepository;
        this.paymentChannelRepository = paymentChannelRepository;
    }

    @Override
    public PaymentApp getById(Long id) {
        return paymentAppRepository.findById(id);
    }

    @Override
    public PaymentApp getByAppCode(String appCode) {
        return paymentAppRepository.findByAppCode(appCode);
    }

    @Override
    public List<PaymentApp> listByChannelId(Long channelId) {
        return paymentAppRepository.listByChannelId(channelId);
    }

    @Override
    public List<PaymentApp> listEnabledApps() {
        return paymentAppRepository.listEnabled();
    }

    @Override
    public List<PaymentApp> listAllApps() {
        return paymentAppRepository.listAll();
    }

    @Override
    public List<PaymentApp> listEnabledAppsByChannelId(Long channelId) {
        return paymentAppRepository.listEnabledByChannelId(channelId);
    }

    @Override
    public PaymentApp createApp(PaymentAppRequest request) {
        validateRequest(request, null);
        PaymentApp app = new PaymentApp();
        copyRequestToEntity(request, app);
        paymentAppRepository.save(app);
        return app;
    }

    @Override
    public PaymentApp updateApp(Long id, PaymentAppRequest request) {
        if (id == null) {
            throw new BizException("应用ID不能为空");
        }
        PaymentApp exist = paymentAppRepository.findById(id);
        if (exist == null) {
            throw new BizException("支付应用不存在");
        }
        validateRequest(request, id);
        copyRequestToEntity(request, exist);
        paymentAppRepository.update(exist);
        return paymentAppRepository.findById(id);
    }

    @Override
    public PaymentApp updateAppStatus(Long id, String status) {
        if (id == null) {
            throw new BizException("应用ID不能为空");
        }
        validateStatus(status);
        PaymentApp app = paymentAppRepository.findById(id);
        if (app == null) {
            throw new BizException("支付应用不存在");
        }
        app.setAppStatus(status);
        paymentAppRepository.update(app);
        return paymentAppRepository.findById(id);
    }

    @Override
    public void deleteApp(Long id) {
        if (id == null) {
            throw new BizException("应用ID不能为空");
        }
        PaymentApp app = paymentAppRepository.findById(id);
        if (app == null) {
            throw new BizException("支付应用不存在");
        }
        paymentAppRepository.deleteById(id);
    }

    private void validateRequest(PaymentAppRequest request, Long currentId) {
        if (request == null) {
            throw new BizException("支付应用参数不能为空");
        }
        if (!StringUtils.hasText(request.getAppName())) {
            throw new BizException("应用名称不能为空");
        }
        if (!StringUtils.hasText(request.getAppCode())) {
            throw new BizException("应用编码不能为空");
        }
        if (request.getChannelId() == null) {
            throw new BizException("支付渠道不能为空");
        }
        PaymentChannel channel = paymentChannelRepository.findById(request.getChannelId());
        if (channel == null) {
            throw new BizException("支付渠道不存在");
        }
        String status = defaultStatus(request.getAppStatus());
        validateStatus(status);

        PaymentApp exist = paymentAppRepository.findByAppCode(request.getAppCode().trim());
        if (exist != null && !exist.getId().equals(currentId)) {
            throw new BizException("应用编码已存在");
        }
    }

    private void copyRequestToEntity(PaymentAppRequest request, PaymentApp app) {
        app.setAppName(request.getAppName().trim());
        app.setAppCode(request.getAppCode().trim());
        app.setAppStatus(defaultStatus(request.getAppStatus()));
        app.setChannelId(request.getChannelId());
        app.setAppDesc(trimToNull(request.getAppDesc()));
        app.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : ENABLED;
    }

    private void validateStatus(String status) {
        if (!ENABLED.equals(status) && !DISABLED.equals(status)) {
            throw new BizException("状态只能是ENABLED或DISABLED");
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
