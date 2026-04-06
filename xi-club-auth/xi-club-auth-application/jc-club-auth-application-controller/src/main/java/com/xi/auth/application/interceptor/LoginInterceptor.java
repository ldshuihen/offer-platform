package com.xi.auth.application.interceptor;

import com.xi.auth.context.LoginContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器，调用的是com.jingdianjichi.auth.context.LoginContextHolder;路径下的LoginContextHolder.java文件
 *
 * @author: ChickenWing
 * @date: 2023/11/26
 * LoginInterceptor.java（核心：登录信息拦截注入）
 * 作用：在 HTTP 请求进入 Controller 之前，从请求头中提取 loginId 并存入 LoginContextHolder。
 * 📌 设计意义：
 * 解耦认证与业务：Controller/Service 无需关心 loginId 从哪来
 * 统一入口：所有请求的登录信息都在此处初始化
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String loginId = request.getHeader("loginId");
        if (StringUtils.isNotBlank(loginId)) {
            LoginContextHolder.set("loginId", loginId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 请求结束后清理（防止 Tomcat 线程复用导致数据污染）,清理（防内存泄漏）
        LoginContextHolder.remove();
    }

}
