package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.mq.OrderCloseMessage;
import cc.ivera.shared.infrastructure.util.JsonUtils;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalMessageServiceImplTest {

    private final LocalMessageMapper mapper = mock(LocalMessageMapper.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private LocalMessageServiceImpl service;

    @BeforeAll
    static void initMybatisLambdaMetadata() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), LocalMessage.class);
    }

    @BeforeEach
    void setUp() {
        service = new LocalMessageServiceImpl(mapper, rabbitTemplate, new SimpleMeterRegistry());
    }

    @Test
    void firstPublishFailureSchedulesThreeSecondRetry() {
        stubSynchronousPublishFailure(0);

        LambdaUpdateWrapper<LocalMessage> update = capturedUpdate();

        assertTrue(update.getParamNameValuePairs().containsValue(1));
        assertTrue(update.getParamNameValuePairs().containsValue(LocalMessage.STATUS_PENDING));
        assertEquals(3_000L, retryDelay(update));
    }

    @Test
    void secondPublishFailureSchedulesNineSecondRetry() {
        stubSynchronousPublishFailure(1);

        LambdaUpdateWrapper<LocalMessage> update = capturedUpdate();

        assertTrue(update.getParamNameValuePairs().containsValue(2));
        assertEquals(9_000L, retryDelay(update));
    }

    @Test
    void sixthPublishFailureMarksMessageFailedWithoutAnotherRetryTime() {
        stubSynchronousPublishFailure(5);

        LambdaUpdateWrapper<LocalMessage> update = capturedUpdate();

        assertTrue(update.getParamNameValuePairs().containsValue(6));
        assertTrue(update.getParamNameValuePairs().containsValue(LocalMessage.STATUS_FAILED));
        assertFalse(update.getSqlSet().contains("next_retry_time"));
    }

    @Test
    void brokerAckTransitionsPendingMessageToSent() {
        LocalMessage message = message(0);
        when(mapper.selectPendingMessages(eq(LocalMessage.STATUS_PENDING), any(), isNull(), eq(100)))
            .thenReturn(List.of(message));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3, CorrelationData.class);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        service.publishPending(null);

        ArgumentCaptor<LambdaUpdateWrapper<LocalMessage>> captor = updateCaptor();
        verify(mapper, timeout(2_000)).update(isNull(), captor.capture());
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue(LocalMessage.STATUS_SENT));
        assertFalse(captor.getValue().getParamNameValuePairs().containsValue(LocalMessage.STATUS_FAILED));
    }

    private void stubSynchronousPublishFailure(int currentRetryCount) {
        when(mapper.selectPendingMessages(eq(LocalMessage.STATUS_PENDING), any(), isNull(), eq(100)))
            .thenReturn(List.of(message(currentRetryCount)));
        doThrow(new IllegalStateException("broker down")).when(rabbitTemplate)
            .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        service.publishPending(null);
    }

    private LambdaUpdateWrapper<LocalMessage> capturedUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<LocalMessage>> captor = updateCaptor();
        verify(mapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<LambdaUpdateWrapper<LocalMessage>> updateCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }

    private static long retryDelay(LambdaUpdateWrapper<LocalMessage> update) {
        List<Date> dates = update.getParamNameValuePairs().values().stream()
            .filter(Date.class::isInstance)
            .map(Date.class::cast)
            .sorted(Comparator.comparingLong(Date::getTime))
            .toList();
        assertEquals(2, dates.size());
        return dates.get(1).getTime() - dates.get(0).getTime();
    }

    private static LocalMessage message(int retryCount) {
        OrderCloseMessage payload = new OrderCloseMessage();
        payload.setOrderNo("ORD-1");
        payload.setPaymentType("WXPAY");
        LocalMessage message = new LocalMessage();
        message.setId(1L);
        message.setBizType(LocalMessage.BIZ_TYPE_ORDER_CLOSE);
        message.setBizNo("ORD-1");
        message.setMessageContent(JsonUtils.toJson(payload));
        message.setStatus(LocalMessage.STATUS_PENDING);
        message.setRetryCount(retryCount);
        return message;
    }
}
