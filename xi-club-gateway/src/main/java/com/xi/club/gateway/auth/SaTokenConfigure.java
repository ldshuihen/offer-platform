package com.xi.club.gateway.auth;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 权限认证的配置器,新建SaTokenConfigure.java，注册 Sa-Token 的全局过滤器
 * 作用：注册 Sa-Token 的全局拦截器，定义 哪些路径需要登录/权限校验。
 *
 * 📌 关键点：
 * SaReactorFilter：Sa-Token 提供的 WebFlux 全局过滤器（适用于响应式编程）。
 * SaRouter.match()：
 * 第一个参数：匹配的 URL 路径（支持通配符 /**）
 * 第二个参数：校验逻辑（如 checkLogin() 检查是否登录，checkPermission("xxx") 检查权限）
 * 未显式排除的路径（如 /user/doLogin）默认不校验（因为代码中注释掉了排除逻辑，但实际通过 SaRouter 的匹配顺序隐式放行）。
 * 💡 本质：这是一个 集中式的 API 网关级权限控制，类似 Spring Security 的 HttpSecurity 配置。
 *
 * @author: ChickenWing
 * @date: 2023/10/28
 */
@Configuration
public class SaTokenConfigure {

    @Bean
    public SaReactorFilter getSaReactorFilter() {
        return new SaReactorFilter()
                // 拦截地址
                .addInclude("/**")
                // 鉴权方法：每次访问进入
                .setAuth(obj -> {
                    System.out.println("-------- 前端访问path：" + SaHolder.getRequest().getRequestPath());
                    // 登录校验 -- 拦截所有路由，并排除/user/doLogin 用于开放登录
//                    SaRouter.match("/auth/**", "/auth/user/doLogin", r -> StpUtil.checkRole("admin"));
                    SaRouter.match("/oss/**", r -> StpUtil.checkLogin());
                    SaRouter.match("/subject/subject/add", r -> StpUtil.checkPermission("subject:add"));
                    SaRouter.match("/subject/**", r -> StpUtil.checkLogin());
                })
                ;
    }
}
