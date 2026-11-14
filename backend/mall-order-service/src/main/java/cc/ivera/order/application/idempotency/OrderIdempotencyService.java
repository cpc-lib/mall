package cc.ivera.order.application.idempotency;

import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.order.domain.enums.OrderRequestStatus;
import cc.ivera.order.domain.model.OrderRequest;
import cc.ivera.order.domain.repository.OrderRequestRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * 下单请求幂等：Redis 锁串行化 + t_order_request 唯一键兜底 + 成功报文回放。
 */
@Service
public class OrderIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(OrderIdempotencyService.class);
    private static final long LOCK_WAIT_MS = 3000L;

    private final OrderRequestRepository orderRequestRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final ObjectMapper objectMapper;

    @Value("${order.request.ttl-hours:24}")
    private long ttlHours;

    public OrderIdempotencyService(OrderRequestRepository orderRequestRepository,
                                   DistributedLockTemplate distributedLockTemplate,
                                   ObjectMapper objectMapper) {
        this.orderRequestRepository = orderRequestRepository;
        this.distributedLockTemplate = distributedLockTemplate;
        this.objectMapper = objectMapper;
    }

    public OrderDetailVO execute(Long userId, String api, String idempotencyKey, Object payload,
                                 Supplier<OrderDetailVO> action) {
        if (!StringUtils.hasText(idempotencyKey)) throw new BizException("缺少 Idempotency-Key");
        String key = idempotencyKey.trim();
        if (key.length() > 128) throw new BizException("Idempotency-Key 长度不能超过 128");
        String requestHash = sha256(payload);
        String lockKey = "order:idem:" + userId + ":" + api + ":" + key;
        return distributedLockTemplate.execute(lockKey, LOCK_WAIT_MS,
            () -> doExecute(userId, api, key, requestHash, action));
    }

    private OrderDetailVO doExecute(Long userId, String api, String idemKey, String requestHash,
                                    Supplier<OrderDetailVO> action) {
        OrderRequest existing = orderRequestRepository.find(idemKey, userId, api);
        if (existing == null) {
            OrderRequest request = OrderRequest.processing(idemKey, userId, api, requestHash, expireAt());
            try {
                orderRequestRepository.insert(request);
            } catch (DuplicateKeyException e) {
                OrderRequest concurrent = orderRequestRepository.find(idemKey, userId, api);
                if (concurrent == null) throw new BizException("下单请求正在处理，请稍后重试");
                return replayOrRetry(concurrent, requestHash, action);
            }
            return runAction(request, action);
        }
        return replayOrRetry(existing, requestHash, action);
    }

    private OrderDetailVO replayOrRetry(OrderRequest existing, String requestHash, Supplier<OrderDetailVO> action) {
        if (!requestHash.equals(existing.getRequestHash()))
            throw new BizException("相同 Idempotency-Key 的请求内容不一致");
        if (OrderRequestStatus.SUCCESS.getType().equals(existing.getStatus()))
            return replay(existing);
        if (OrderRequestStatus.FAILED.getType().equals(existing.getStatus())) {
            if (orderRequestRepository.casFailedToProcessing(existing.getId(), requestHash) == 0)
                throw new BizException("下单请求正在处理，请稍后重试");
            return runAction(existing, action);
        }
        throw new BizException("下单请求正在处理，请稍后重试");
    }

    private OrderDetailVO runAction(OrderRequest request, Supplier<OrderDetailVO> action) {
        OrderDetailVO result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            orderRequestRepository.markFailed(request.getId());
            throw e;
        }
        String orderNo = result.getOrder() == null ? null : result.getOrder().getOrderNo();
        orderRequestRepository.markSuccess(request.getId(), orderNo, write(result));
        return result;
    }

    private OrderDetailVO replay(OrderRequest request) {
        if (!StringUtils.hasText(request.getResponseBody()))
            throw new BizException("下单结果尚未就绪，请稍后重试");
        try {
            return objectMapper.readValue(request.getResponseBody(), OrderDetailVO.class);
        } catch (Exception e) {
            log.error("幂等回放报文解析失败，orderNo={}", request.getOrderNo(), e);
            throw new BizException("下单结果读取失败，请稍后重试");
        }
    }

    private String write(OrderDetailVO result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("下单幂等报文序列化失败", e);
            return null;
        }
    }

    private Date expireAt() {
        return new Date(System.currentTimeMillis() + ttlHours * 3600_000L);
    }

    private String sha256(Object payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException("下单请求规范化失败", e);
        }
    }
}
