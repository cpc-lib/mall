package cc.ivera.service.impl;

import cc.ivera.dto.PaymentAppRequest;
import cc.ivera.entity.PaymentApp;
import cc.ivera.entity.PaymentChannel;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.PaymentAppMapper;
import cc.ivera.service.PaymentAppService;
import cc.ivera.service.PaymentChannelService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Service
public class PaymentAppServiceImpl extends ServiceImpl<PaymentAppMapper, PaymentApp> implements PaymentAppService {

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    private final PaymentChannelService paymentChannelService;
    private final ObjectMapper objectMapper;

    public PaymentAppServiceImpl(PaymentChannelService paymentChannelService, ObjectMapper objectMapper) {
        this.paymentChannelService = paymentChannelService;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentApp getByAppCode(String appCode) {
        LambdaQueryWrapper<PaymentApp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentApp::getAppCode, appCode);
        return baseMapper.selectOne(queryWrapper);
    }

    @Override
    public List<PaymentApp> listByChannelId(Long channelId) {
        LambdaQueryWrapper<PaymentApp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentApp::getChannelId, channelId)
                .orderByAsc(PaymentApp::getSortOrder)
                .orderByDesc(PaymentApp::getUpdateTime);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<PaymentApp> listEnabledApps() {
        LambdaQueryWrapper<PaymentApp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentApp::getAppStatus, ENABLED)
                .orderByAsc(PaymentApp::getSortOrder)
                .orderByDesc(PaymentApp::getUpdateTime);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<PaymentApp> listAllApps() {
        LambdaQueryWrapper<PaymentApp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(PaymentApp::getSortOrder)
                .orderByDesc(PaymentApp::getUpdateTime);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<PaymentApp> listEnabledAppsByChannelId(Long channelId) {
        LambdaQueryWrapper<PaymentApp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentApp::getChannelId, channelId)
                .eq(PaymentApp::getAppStatus, ENABLED)
                .orderByAsc(PaymentApp::getSortOrder)
                .orderByDesc(PaymentApp::getUpdateTime);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public PaymentApp createApp(PaymentAppRequest request) {
        validateRequest(request, null);
        PaymentApp app = new PaymentApp();
        copyRequestToEntity(request, app);
        baseMapper.insert(app);
        return app;
    }

    @Override
    public PaymentApp updateApp(Long id, PaymentAppRequest request) {
        if (id == null) {
            throw new BizException("应用ID不能为空");
        }
        PaymentApp exist = baseMapper.selectById(id);
        if (exist == null) {
            throw new BizException("支付应用不存在");
        }
        validateRequest(request, id);
        copyRequestToEntity(request, exist);
        baseMapper.updateById(exist);
        return baseMapper.selectById(id);
    }

    @Override
    public PaymentApp updateAppStatus(Long id, String status) {
        if (id == null) {
            throw new BizException("应用ID不能为空");
        }
        validateStatus(status);
        PaymentApp app = baseMapper.selectById(id);
        if (app == null) {
            throw new BizException("支付应用不存在");
        }
        app.setAppStatus(status);
        baseMapper.updateById(app);
        return baseMapper.selectById(id);
    }

    @Override
    public void deleteApp(Long id) {
        if (id == null) {
            throw new BizException("应用ID不能为空");
        }
        PaymentApp app = baseMapper.selectById(id);
        if (app == null) {
            throw new BizException("支付应用不存在");
        }
        baseMapper.deleteById(id);
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
        PaymentChannel channel = paymentChannelService.getById(request.getChannelId());
        if (channel == null) {
            throw new BizException("支付渠道不存在");
        }
        String status = defaultStatus(request.getAppStatus());
        validateStatus(status);
        validateJsonObject(request.getAppConfig(), "应用配置参数必须是JSON对象");

        LambdaQueryWrapper<PaymentApp> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaymentApp::getAppCode, request.getAppCode().trim());
        PaymentApp exist = baseMapper.selectOne(queryWrapper);
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
        app.setAppConfig(trimToNull(request.getAppConfig()));
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
