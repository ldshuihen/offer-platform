package com.xi.subject.infra.rpc;

import com.xi.auth.api.UserFeignService;
import com.xi.auth.entity.AuthUserDTO;
import com.xi.auth.entity.Result;
import com.xi.subject.infra.entity.UserInfo;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 是一个典型的 微服务间远程调用的封装类（RPC 适配层），其核心作用是：
 * 在 subject 服务中，通过 Feign 客户端安全、简洁地调用 auth 认证服务的用户信息接口，并将返回结果转换为本服务所需的 UserInfo 对象。
 * ✅ 一、整体定位：防腐层（Anti-Corruption Layer, ACL）
 * 在 DDD 或微服务架构中，直接使用其他服务的 DTO/接口会污染本领域模型。
 * UserRpc 的作用就是：
 * 隔离外部服务细节（AuthUserDTO, Result<T>）
 * 提供本服务专用的接口（返回 UserInfo）
 * 处理远程调用异常/失败逻辑
 * 🛡️ 这是微服务集成的最佳实践 —— 避免“上游服务变更导致下游大面积修改”。
 */
@Component
public class UserRpc {

    // 1. 依赖注入 Feign Client
    @Resource
    private UserFeignService userFeignService;

    // 2. 封装远程调用逻辑
    public UserInfo getUserInfo(String userName) {
        // 1. 构造 auth 服务需要的请求参数
        AuthUserDTO authUserDTO = new AuthUserDTO();
        authUserDTO.setUserName(userName);
        // 2. 远程调用 auth 服务
        Result<AuthUserDTO> result = userFeignService.getUserInfo(authUserDTO);
        // 3. 处理失败情况（防御性编程）
        UserInfo userInfo = new UserInfo();
        if (!result.getSuccess()) {
            return userInfo;    // 返回空对象而非 null，避免 NPE
        }
        // 4. 转换数据：auth 服务 DTO → 本服务实体
        AuthUserDTO data = result.getData();
        userInfo.setUserName(data.getUserName());
        userInfo.setNickName(data.getNickName());
        userInfo.setAvatar(data.getAvatar());
        return userInfo;
    }

}
