package cc.ivera.gateway.error;

import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关统一错误响应：把 404/503/504 等非业务错误包装为固定 JSON 结构，避免前端收到空响应。
 * HTTP 状态码保持真实语义，响应体固定为 {code:500,message,data:null}。
 */
@Component
@Order(-1)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        HttpStatus status = resolveStatus(ex);
        String message = status == HttpStatus.NOT_FOUND
            ? "请求的资源不存在"
            : "网关服务暂不可用，请稍后重试";
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"code\":500,\"message\":\"" + message + "\",\"data\":null}")
            .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private HttpStatus resolveStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException statusException
            && statusException.getStatusCode() instanceof HttpStatus status) {
            return status;
        }
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
