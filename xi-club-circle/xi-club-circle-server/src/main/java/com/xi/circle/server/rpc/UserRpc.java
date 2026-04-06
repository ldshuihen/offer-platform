package com.jingdianjichi.circle.server.rpc;

import com.xi.auth.api.UserFeignService;
import com.xi.auth.entity.AuthUserDTO;
import com.xi.auth.entity.Result;
import com.jingdianjichi.circle.server.entity.dto.UserInfo;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/*

作用是：作为“用户中心”的远程代理，专门负责在当前服务（圈子服务）中调用“用户服务”的接口。
它是微服务架构中服务间通信的具体实现。简单来说，它的存在是为了让“圈子服务”能够安全、方便地获取到“用户服务”里的数据（如用户名、昵称、头像）。
我们可以从以下几个维度来详细拆解它的作用：
核心角色：RPC 客户端代理
这个类通常被称为 Feign Client 或 RPC Facade。
位置：它位于“圈子服务”（Circle Service）的代码中。
使命：它不处理具体的业务逻辑，只负责帮圈子服务去远程调用“用户服务”（User Service）的 RESTful API。
类比：就像你去餐厅吃饭（圈子服务），想点一瓶酒（用户数据），你不需要自己去酒窖拿，而是告诉服务员（UserRpc），服务员去帮你拿来。
具体功能拆解
单个查询：getUserInfo(String userName)
作用：根据用户名，远程调用用户服务，获取该用户的详细信息（昵称、头像等）。
流程：
    封装请求参数 AuthUserDTO。
    调用 userFeignService.getUserInfo()（这是一个 HTTP 请求，底层由 OpenFeign 框架实现）。
    接收返回的 Result 对象，解析出数据。
    将用户服务返回的 AuthUserDTO 转换 成圈子服务内部使用的 UserInfo。
注意：如果调用失败（!result.getSuccess()），它会返回一个空的 UserInfo 对象，避免因为用户服务异常导致圈子服务直接报错（这是一种容错处理）。

批量查询：batchGetUserInfo(List userNameList)
作用：根据用户名列表，一次性批量获取多个用户的信息。
价值：在微服务中，批量查询非常关键。如果在前端循环调用单个查询接口，网络延迟会累加（N+1 问题）；而批量查询只需要一次网络往返，能显著降低耗时。
逻辑：
    检查入参是否为空。
    调用用户服务的批量接口 listUserInfoByIds。
    将返回的用户列表转换成一个 Map（Key 是用户名，Value 是用户信息），方便后续代码通过用户名快速查找到对应的用户信息。

总结
这段 UserRpc 代码就是圈子服务通往用户服务的“数据通道”。它封装了网络调用的细节，提供了简单的 Java 方法供业务层调用，
并且通过数据转换保护了本服务的业务模型不受外部服务变更的影响。
 */
@Component
public class UserRpc {

    @Resource
    private UserFeignService userFeignService;

    public UserInfo getUserInfo(String userName) {
        AuthUserDTO authUserDTO = new AuthUserDTO();
        authUserDTO.setUserName(userName);
        Result<AuthUserDTO> result = userFeignService.getUserInfo(authUserDTO);
        UserInfo userInfo = new UserInfo();
        if (!result.getSuccess()) {
            return userInfo;
        }
        AuthUserDTO data = result.getData();
        userInfo.setUserName(data.getUserName());
        userInfo.setNickName(data.getNickName());
        userInfo.setAvatar(data.getAvatar());
        return userInfo;
    }

    public Map<String, UserInfo> batchGetUserInfo(List<String> userNameList) {
        if (CollectionUtils.isEmpty(userNameList)) {
            return Collections.emptyMap();
        }
        Result<List<AuthUserDTO>> listResult = userFeignService.listUserInfoByIds(userNameList);
        if (Objects.isNull(listResult) || !listResult.getSuccess() || Objects.isNull(listResult.getData())) {
            return Collections.emptyMap();
        }
        Map<String, UserInfo> result = new HashMap<>();
        for (AuthUserDTO data : listResult.getData()) {
            UserInfo userInfo = new UserInfo();
            userInfo.setUserName(data.getUserName());
            userInfo.setNickName(data.getNickName());
            userInfo.setAvatar(data.getAvatar());
            result.put(userInfo.getUserName(), userInfo);
        }
        return result;
    }

}
