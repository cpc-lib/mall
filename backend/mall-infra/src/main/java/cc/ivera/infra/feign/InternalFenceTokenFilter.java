package cc.ivera.infra.feign;

import cc.ivera.shared.domain.lock.FenceTokenHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部服务 Feign 围栏令牌接收 Filter。
 * <p>
 * 与 {@link InternalFeignConfiguration#fenceTokenPropagator()} 配合：
 * 调用方在 Feign 请求头注入 {@code X-Fence-Token}，
 * 接收方（/internal/**）在请求生命周期内将令牌绑定到 {@link FenceTokenHolder}，
 * 让跨服务的 Repository 更新也能携带 {@code fence_token <= newToken} CAS 条件。
 * <p>
 * 注意：
 * - 仅当本地线程未持锁时才绑定传入令牌。本地已有锁时（理论上跨 /internal/** 的自嵌套极少），
 *   其围栏令牌由 Redisson 锁生成，一定 ≥ Feign 传来的令牌（同一全局单调序列），不应覆盖。
 * - 令牌绑定是临时的：请求结束后 {@link FenceTokenHolder#release()} 清理，避免线程池复用时残留。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // 在 AuthInterceptor 之前，确保下游 Mapper 都能读到
@Slf4j
public class InternalFenceTokenFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 仅处理 /internal/**，其他路径跳过（包括 /api/**）
        return uri == null || !uri.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String headerValue = request.getHeader(InternalFeignConfiguration.FENCE_TOKEN_HEADER);
        Long incomingToken = null;
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                incomingToken = Long.parseLong(headerValue.trim());
                if (incomingToken <= 0) incomingToken = null;
            } catch (NumberFormatException ignore) {
                log.warn("非法的围栏令牌请求头，header={}", headerValue);
            }
        }

        boolean bound = false;
        if (incomingToken != null && FenceTokenHolder.get() == null) {
            // 本地未持锁 → 绑定 Feign 传来的令牌
            FenceTokenHolder.acquire("feign-internal", incomingToken);
            bound = true;
            log.debug("绑定 Feign 围栏令牌，token={}, uri={}", incomingToken, request.getRequestURI());
        } else if (incomingToken != null && FenceTokenHolder.get() != null) {
            // 本地已持锁 → 跳过绑定，本地令牌是同一全局序列上的更大值，不该被覆盖
            log.debug("本地锁已持有，跳过 Feign 围栏令牌绑定，local={}, incoming={}",
                FenceTokenHolder.get(), incomingToken);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            if (bound) {
                FenceTokenHolder.release();
            }
        }
    }
}
