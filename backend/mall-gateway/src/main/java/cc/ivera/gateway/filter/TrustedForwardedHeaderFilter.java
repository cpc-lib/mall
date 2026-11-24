package cc.ivera.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 覆盖客户端传入的 X-Forwarded-For，只保留网关观测到的真实来源地址。
 * 下游按首个 XFF 取值作为客户端 IP，若不覆盖会被伪造头欺骗（登录失败计数、审计 IP 污染）。
 */
@Component
public class TrustedForwardedHeaderFilter implements GlobalFilter, Ordered {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return chain.filter(exchange);
        }
        String clientIp = remoteAddress.getAddress() == null
            ? remoteAddress.getHostString()
            : remoteAddress.getAddress().getHostAddress();
        ServerHttpRequest request = exchange.getRequest().mutate()
            .headers(headers -> headers.set(X_FORWARDED_FOR, clientIp))
            .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
