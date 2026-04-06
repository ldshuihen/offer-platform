package com.xi.club.gateway.exception;

import cn.dev33.satoken.exception.SaTokenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xi.club.gateway.entity.Result;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关全局异常处理
 *
 * GatewayExceptionHandler.java —— 网关全局异常处理器
 * 作用：捕获网关层面的所有未处理异常，统一返回结构化错误响应（避免暴露原始堆栈）。
 * ErrorWebExceptionHandler：Spring WebFlux 提供的全局异常处理接口（专用于响应式编程）
 * 💡 本质：这是一个 响应式全局异常兜底策略，确保任何异常都不会导致网关返回空白页或原始错误。
 * @author: ChickenWing
 * @date: 2023/10/28
 */
@Component
// 1. 实现 ErrorWebExceptionHandler
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private ObjectMapper objectMapper = new ObjectMapper();

    // 2. 重写 handle() 方法
    @Override
    public Mono<Void> handle(ServerWebExchange serverWebExchange, Throwable throwable) {
        ServerHttpRequest request = serverWebExchange.getRequest();
        ServerHttpResponse response = serverWebExchange.getResponse();

        // 3. 异常分类处理
        Integer code = 200;
        String message = "";
        if (throwable instanceof SaTokenException) {
            code = 401;
            message = "用户无权限";
            throwable.printStackTrace();
        } else {
            code = 500;
            message = "系统繁忙";
            throwable.printStackTrace();
        }

        // 4. 构建统一响应体,设置 JSON 响应头：确保前端能正确解析
        Result result = Result.fail(code, message);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // DataBuffer：WebFlux 的底层数据缓冲区
        // Mono.fromSupplier：非阻塞方式生成响应数据
        // 5. 写入响应（响应式流）
        return response.writeWith(Mono.fromSupplier(() -> {
            DataBufferFactory dataBufferFactory = response.bufferFactory();
            byte[] bytes = null;
            try {
                bytes = objectMapper.writeValueAsBytes(result);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return dataBufferFactory.wrap(bytes);
        }));
    }

}
