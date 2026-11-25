package cc.ivera.payment.application.impl;

import cc.ivera.payment.application.dto.PaymentChannelRequest;
import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.domain.repository.PaymentChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 支付渠道凭据掩码单测：HTTP 返回必须脱敏，编辑回传掩码时保留数据库原值。
 */
class PaymentChannelServiceImplTest {

    private static final String MASK = "******";

    private final PaymentChannelRepository repository = mock(PaymentChannelRepository.class);
    private final PaymentChannelServiceImpl service = new PaymentChannelServiceImpl(repository, new ObjectMapper());

    @Test
    void listAllMasksMerchantSecrets() {
        when(repository.listAll()).thenReturn(List.of(channel(1L, "REAL")));

        List<PaymentChannel> channels = service.listAllChannels();

        PaymentChannel view = channels.get(0);
        assertEquals(MASK, view.getPrivateKey());
        assertEquals(MASK, view.getApiV3Key());
        assertEquals(MASK, view.getPartnerKey());
        assertEquals(MASK, view.getMerchantPrivateKey());
        assertEquals("APP-ID", view.getAppid());
    }

    @Test
    void updateKeepsStoredSecretWhenRequestSendsMask() {
        PaymentChannel existing = channel(1L, "REAL");
        when(repository.findById(1L)).thenReturn(existing);
        when(repository.findByChannelCode("WXPAY")).thenReturn(existing);
        AtomicReference<String> persisted = new AtomicReference<>();
        doAnswer(invocation -> {
            persisted.set(((PaymentChannel) invocation.getArgument(0)).getPrivateKey());
            return null;
        }).when(repository).update(any());

        PaymentChannelRequest request = request();
        request.setPrivateKey(MASK);
        service.updateChannel(1L, request);

        assertEquals("REAL", persisted.get());
    }

    @Test
    void getByChannelCodeKeepsRawSecretForSigning() {
        when(repository.findByChannelCode("WXPAY")).thenReturn(channel(1L, "REAL"));

        assertEquals("REAL", service.getByChannelCode("WXPAY").getPrivateKey());
    }

    private static PaymentChannel channel(Long id, String secret) {
        PaymentChannel channel = new PaymentChannel();
        channel.setId(id);
        channel.setChannelName("微信支付");
        channel.setChannelCode("WXPAY");
        channel.setChannelStatus("ENABLED");
        channel.setAppid("APP-ID");
        channel.setPrivateKey(secret);
        channel.setApiV3Key(secret);
        channel.setPartnerKey(secret);
        channel.setMerchantPrivateKey(secret);
        channel.setSortOrder(0);
        return channel;
    }

    private static PaymentChannelRequest request() {
        PaymentChannelRequest request = new PaymentChannelRequest();
        request.setChannelName("微信支付");
        request.setChannelCode("WXPAY");
        request.setChannelStatus("ENABLED");
        return request;
    }
}
