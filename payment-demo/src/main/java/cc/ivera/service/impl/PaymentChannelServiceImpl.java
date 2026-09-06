package cc.ivera.service.impl;

import cc.ivera.dto.PaymentChannelRequest;
import cc.ivera.entity.PaymentChannel;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.PaymentChannelMapper;
import cc.ivera.service.PaymentChannelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Service
public class PaymentChannelServiceImpl extends ServiceImpl<PaymentChannelMapper, PaymentChannel> implements PaymentChannelService {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private final ObjectMapper objectMapper;

    public PaymentChannelServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentChannel getByChannelCode(String channelCode) {
        LambdaQueryWrapper<PaymentChannel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentChannel::getChannelCode, channelCode);
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public List<PaymentChannel> listEnabledChannels() {
        LambdaQueryWrapper<PaymentChannel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentChannel::getChannelStatus, ENABLED)
                .orderByAsc(PaymentChannel::getSortOrder)
                .orderByDesc(PaymentChannel::getUpdateTime);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<PaymentChannel> listAllChannels() {
        LambdaQueryWrapper<PaymentChannel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(PaymentChannel::getSortOrder)
                .orderByDesc(PaymentChannel::getUpdateTime);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public PaymentChannel createChannel(PaymentChannelRequest request) {
        validateRequest(request, null);

        PaymentChannel channel = new PaymentChannel();
        copyRequestToEntity(request, channel);
        baseMapper.insert(channel);
        return channel;
    }

    @Override
    public PaymentChannel updateChannel(Long id, PaymentChannelRequest request) {
        if (id == null) {
            throw new BizException("渠道ID不能为空");
        }
        PaymentChannel exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BizException("支付渠道不存在");
        }
        validateRequest(request, id);
        copyRequestToEntity(request, exist);
        baseMapper.updateById(exist);
        return baseMapper.selectById(id);
    }

    @Override
    public PaymentChannel updateChannelStatus(Long id, String status) {
        if (id == null) {
            throw new BizException("渠道ID不能为空");
        }
        validateStatus(status);
        PaymentChannel channel = baseMapper.selectById(id);
        if (channel == null) {
            throw new BizException("支付渠道不存在");
        }
        channel.setChannelStatus(status);
        baseMapper.updateById(channel);
        return baseMapper.selectById(id);
    }

    @Override
    public void deleteChannel(Long id) {
        if (id == null) {
            throw new BizException("渠道ID不能为空");
        }
        PaymentChannel channel = baseMapper.selectById(id);
        if (channel == null) {
            throw new BizException("支付渠道不存在");
        }
        baseMapper.deleteById(id);
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

        LambdaQueryWrapper<PaymentChannel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentChannel::getChannelCode, request.getChannelCode().trim());
        PaymentChannel exist = baseMapper.selectOne(queryWrapper);
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
