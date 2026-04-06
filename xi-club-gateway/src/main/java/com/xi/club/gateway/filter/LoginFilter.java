package com.xi.club.gateway.filter;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 登录拦截器
 * LoginFilter.java —— 网关登录认证过滤器
 * 作用：在请求转发到微服务前，验证用户登录状态，并将用户身份信息透传给下游服务。
 * 💡 本质：这是一个 认证 + 上下文传递 过滤器，实现：
 * 拦截非登录请求 → 验证 Token 有效性
 * 将用户身份 透传给微服务，避免微服务重复解析 Token
 * @author: ChickenWing
 * @date: 2023/11/26
 */
// 1. 实现 GlobalFilter
// @Component：注册为 Spring Bean
// GlobalFilter：Spring Cloud Gateway 的全局过滤器接口
// @Slf4j：Lombok 自动生成日志对象
@Component
@Slf4j
public class LoginFilter implements GlobalFilter {

    // 2. 重写 filter() 方法
    @Override
    @SneakyThrows
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        ServerHttpRequest.Builder mutate = request.mutate();
        String url = request.getURI().getPath();
        log.info("LoginFilter.filter.url:{}", url);
        // 3. 放行登录接口,关键设计：登录接口必须跳过认证，否则无法登录
        if (url.equals("/user/doLogin")) {
            return chain.filter(exchange);
        }
        // 4. 获取当前登录用户信息
        // StpUtil：Sa-Token 的核心工具类
        // getTokenInfo()：从请求头（默认 satoken）解析 Token 并获取用户信息
        // 假设：前端已在请求头携带有效 Token
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        log.info("LoginFilter.filter.url:{}", new Gson().toJson(tokenInfo));
        // 5. 透传用户身份到下游服务
        // 关键操作：在转发请求前，注入 loginId 请求头
        // 下游服务（如 auth-service）可通过 @RequestHeader("loginId") 获取当前用户 ID
        String loginId = (String) tokenInfo.getLoginId();
        mutate.header("loginId", loginId);
        return chain.filter(exchange.mutate().request(mutate.build()).build());
    }

}
