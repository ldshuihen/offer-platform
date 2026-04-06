package com.xi.club.gateway.auth;

import cn.dev33.satoken.stp.StpInterface;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xi.club.gateway.entity.AuthPermission;
import com.xi.club.gateway.entity.AuthRole;
import com.xi.club.gateway.redis.RedisUtil;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义权限验证接口扩展
 * StpInterfaceImpl.java —— 自定义权限数据源
 * 作用：告诉 Sa-Token 如何获取当前用户的角色和权限列表（从 Redis 读取）。
 * 📌 关键点：
 * 实现 StpInterface 接口：这是 Sa-Token 的扩展点，用于自定义权限数据源。
 * 数据存储位置：Redis（键格式：auth.permission:{loginId} 和 auth.role:{loginId}）。
 * 数据格式：JSON 字符串（通过 Gson 反序列化为 AuthRole/AuthPermission 对象列表）。
 * 返回值：角色/权限的 字符串标识列表（如 ["admin", "user"] 或 ["subject:add", "user:delete"]）。
 * 💡 本质：这是一个 权限数据适配器，将你的业务数据（Redis 中的 JSON）转换为 Sa-Token 能识别的格式。
 * @author: ChickenWing
 * @date: 2023/10/28
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private RedisUtil redisUtil;

    private String authPermissionPrefix = "auth.permission";

    private String authRolePrefix = "auth.role";

    // 1. 获取用户权限列表
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return getAuth(loginId.toString(), authPermissionPrefix);
    }

    // 2. 获取用户角色列表
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return getAuth(loginId.toString(), authRolePrefix);
    }

    // 3. 从 Redis 读取并解析权限/角色数据
    private List<String> getAuth(String loginId, String prefix) {
        String authKey = redisUtil.buildKey(prefix, loginId.toString());  // 如: auth.permission:123
        String authValue = redisUtil.get(authKey);
        if (StringUtils.isBlank(authValue)) {
            return Collections.emptyList();
        }
        List<String> authList = new LinkedList<>();
        // 根据前缀解析不同数据类型
        if (authRolePrefix.equals(prefix)) {
            // 反序列化为 AuthRole 列表 → 提取 roleKey
            List<AuthRole> roleList = new Gson().fromJson(authValue, new TypeToken<List<AuthRole>>() {
            }.getType());
            authList = roleList.stream().map(AuthRole::getRoleKey).collect(Collectors.toList());
        } else if (authPermissionPrefix.equals(prefix)) {
            // 反序列化为 AuthPermission 列表 → 提取 permissionKey
            List<AuthPermission> permissionList = new Gson().fromJson(authValue, new TypeToken<List<AuthPermission>>() {
            }.getType());
            authList = permissionList.stream().map(AuthPermission::getPermissionKey).collect(Collectors.toList());
        }
        return authList;
    }

}
