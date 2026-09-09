package cc.ivera.payment.application.impl;

import cc.ivera.payment.application.PaymentChannelService;
import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.domain.repository.PaymentChannelRepository;
import cc.ivera.payment.interfaces.dto.PaymentChannelRequest;
import cc.ivera.shared.domain.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Service
public class PaymentChannelServiceImpl implements PaymentChannelService {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private final PaymentChannelRepository paymentChannelRepository;
    private final ObjectMapper objectMapper;

    public PaymentChannelServiceImpl(PaymentChannelRepository paymentChannelRepository,
                                     ObjectMapper objectMapper) {
        this.paymentChannelRepository = paymentChannelRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentChannel getById(Long id) {
        return paymentChannelRepository.findById(id);
    }

    @Override
    public PaymentChannel getByChannelCode(String channelCode) {
        return paymentChannelRepository.findByChannelCode(channelCode);
    }

    @Override
    public List<PaymentChannel> listEnabledChannels() {
        return paymentChannelRepository.listEnabled();
    }

    @Override
    public List<PaymentChannel> listAllChannels() {
        return paymentChannelRepository.listAll();
    }

    @Override
    public PaymentChannel createChannel(PaymentChannelRequest request) {
        validateRequest(request, null);

        PaymentChannel channel = new PaymentChannel();
        copyRequestToEntity(request, channel);
        paymentChannelRepository.save(channel);
        return channel;
    }

    @Override
    public PaymentChannel updateChannel(Long id, PaymentChannelRequest request) {
        if (id == null) {
            throw new BizException("渠道ID不能为空");
        }
        PaymentChannel exist = paymentChannelRepository.findById(id);
        if (exist == null) {
            throw new BizException("支付渠道不存在");
        }
        validateRequest(request, id);
        copyRequestToEntity(request, exist);
        paymentChannelRepository.update(exist);
        return paymentChannelRepository.findById(id);
    }

    @Override
    public PaymentChannel updateChannelStatus(Long id, String status) {
        if (id == null) {
            throw new BizException("渠道ID不能为空");
        }
        validateStatus(status);
        PaymentChannel channel = paymentChannelRepository.findById(id);
        if (channel == null) {
            throw new BizException("支付渠道不存在");
        }
        channel.setChannelStatus(status);
        paymentChannelRepository.update(channel);
        return paymentChannelRepository.findById(id);
    }

    @Override
    public void deleteChannel(Long id) {
        if (id == null) {
            throw new BizException("渠道ID不能为空");
        }
        PaymentChannel channel = paymentChannelRepository.findById(id);
        if (channel == null) {
            throw new BizException("支付渠道不存在");
        }
        paymentChannelRepository.deleteById(id);
    }

    private void validateRequest(PaymentChannelRequest request, Long currentId) {
        if (request == null) {
            throw new BizException("支付渠道参数不能为空");
        }
        if (!StringUtils.hasText(request.getChannelName())) {
            throw new BizException("渠道名称不能为空");
        }
        if (!StringUtils.hasText(request.getChannelCode())) {
            throw new BizException("渠道编码不能为空");
        }
        String status = defaultStatus(request.getChannelStatus());
        validateStatus(status);
        validateJsonObject(request.getConfigParams(), "渠道配置参数必须是JSON对象");

        PaymentChannel exist = paymentChannelRepository.findByChannelCode(request.getChannelCode().trim());
        if (exist != null && !exist.getId().equals(currentId)) {
            throw new BizException("渠道编码已存在");
        }
    }

    private void copyRequestToEntity(PaymentChannelRequest request, PaymentChannel channel) {
        channel.setChannelName(request.getChannelName().trim());
        channel.setChannelCode(request.getChannelCode().trim());
        channel.setChannelStatus(defaultStatus(request.getChannelStatus()));
        channel.setChannelDesc(trimToNull(request.getChannelDesc()));
        channel.setConfigParams(trimToNull(request.getConfigParams()));
        // 微信商户信息
        channel.setAppid(trimToNull(request.getAppid()));
        channel.setMchId(trimToNull(request.getMchId()));
        channel.setMchSerialNo(trimToNull(request.getMchSerialNo()));
        channel.setPrivateKey(trimToNull(request.getPrivateKey()));
        channel.setApiV3Key(trimToNull(request.getApiV3Key()));
        channel.setPartnerKey(trimToNull(request.getPartnerKey()));
        // 支付宝商户信息
        channel.setAlipayAppId(trimToNull(request.getAlipayAppId()));
        channel.setSellerId(trimToNull(request.getSellerId()));
        channel.setMerchantPrivateKey(trimToNull(request.getMerchantPrivateKey()));
        channel.setAlipayPublicKey(trimToNull(request.getAlipayPublicKey()));
        channel.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
    }

    private String defaultStatus(String status) {
        return StringUtils.hasText(status) ? status.trim() : ENABLED;
    }

    private void validateStatus(String status) {
        if (!ENABLED.equals(status) && !DISABLED.equals(status)) {
            throw new BizException("状态只能是ENABLED或DISABLED");
        }
    }

    private void validateJsonObject(String json, String message) {
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            if (!objectMapper.readTree(json).isObject()) {
                throw new BizException(message);
            }
        } catch (IOException e) {
            throw new BizException(message, e);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
