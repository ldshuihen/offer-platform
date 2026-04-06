package com.xi.auth.api;

import com.xi.auth.entity.AuthUserDTO;
import com.xi.auth.entity.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * 用户服务feign
 *定义了一个 Feign 客户端接口，用于在微服务架构中 以声明式方式远程调用 jc-club-auth-dev 服务（认证中心）的用户信息接口。
 * ✅ 一、核心作用
 * 让其他微服务（如题目服务 subject）能像调用本地方法一样，安全、简洁地获取用户数据，而无需关心 HTTP 细节。
 * 这是 微服务间通信的标准实践，属于 “服务契约” 的一部分。
 * UserFeignService：定义“我能调什么”
 * UserRpc：封装“我怎么用它”（防腐层）
 * jc-club-auth-dev：提供实际业务逻辑
 * @author: ChickenWing
 * @date: 2023/12/3
 */
//1. @FeignClient("jc-club-auth-dev")
//声明这是一个 Feign 客户端
//"jc-club-auth-dev" 是 目标服务在注册中心（如 Nacos/Eureka）中的服务名
//Spring Cloud 会自动：
//从注册中心发现该服务的 IP:Port 列表
//负载均衡（默认 Ribbon/LoadBalancer）
//发起 HTTP 请求
//📌 注意：jc-club-auth-dev 应该是 认证服务（auth-service）部署时注册的服务名。
@FeignClient("jc-club-auth-dev")
public interface UserFeignService {

    @RequestMapping("/user/getUserInfo")
    Result<AuthUserDTO> getUserInfo(@RequestBody AuthUserDTO authUserDTO);

    @RequestMapping("/user/listByIds")
    Result<List<AuthUserDTO>> listUserInfoByIds(@RequestBody List<String> userNameList);

}
