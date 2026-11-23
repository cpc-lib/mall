package cc.ivera.infra.feign;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.domain.lock.FenceTokenHolder;
import cc.ivera.shared.web.R;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;

/**
 * 内部服务 Feign 公共配置：R 信封错误解码 + 围栏令牌透传。
 * <p>
 * 连接/读超时统一由 {@code spring.cloud.openfeign.client.config} 配置，便于按目标服务覆盖。
 * 只通过 {@code @FeignClient(configuration = ...)} 引用，不加 @Configuration，
 * 避免被主上下文扫描成全局配置而影响微信/支付宝等外部渠道客户端。
 */
public class InternalFeignConfiguration {

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final String FENCE_TOKEN_HEADER = "X-Fence-Token";

    @Bean
    public ErrorDecoder internalServiceErrorDecoder() {
        return (methodKey, response) -> decode(response);
    }

    /**
     * 围栏令牌拦截器：当当前线程持有分布式锁时，将 {@link FenceTokenHolder} 中的围栏令牌
     * 作为请求头 {@code X-Fence-Token} 注入到所有 Feign 请求。
     * <p>
     * 配合服务端 {@code InternalFenceTokenFilter} 可让跨服务的状态推进也携带围栏令牌，
     * 执行数据库 {@code fence_token <= newToken} 的 CAS 条件更新。
     * 未持锁线程不注入 Header，服务端也不会绑定令牌，Mapper XML 的围栏 CAS 自动跳过。
     */
    @Bean
    public RequestInterceptor fenceTokenPropagator() {
        return (RequestTemplate template) -> {
            Long token = FenceTokenHolder.get();
            if (token != null) {
                template.header(FENCE_TOKEN_HEADER, String.valueOf(token));
            }
        };
    }

    private BizException decode(Response response) {
        try (InputStream body = response.body() == null ? null : response.body().asInputStream()) {
            if (body != null) {
                R<?> result = ERROR_MAPPER.readValue(body, R.class);
                return toBizException(result.getCode(), result.getMessage());
            }
        } catch (IOException | RuntimeException ignored) {
            // 非统一信封响应回落到 HTTP 状态错误
        }
        return new BizException(ErrorCode.SYSTEM_ERROR, "内部服务调用失败，HTTP " + response.status());
    }

    private BizException toBizException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode()) {
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
            }
        }
        return new BizException(message == null ? "内部服务调用失败" : message);
    }
}
