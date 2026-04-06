package com.xi.auth.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录上下文对象
 *
 * @author: ChickenWing
 * @date: 2023/11/26
 *
 * LoginContextHolder.java（核心：登录上下文存储）
 * 作用：在当前线程中存储和获取登录用户信息（如 loginId），实现线程隔离的安全上下文。
 * ✅ 关键特性：
 * 基于 InheritableThreadLocal
 * 每个线程有独立的 Map<String, Object> 存储空间
 * 子线程可继承父线程的上下文（适合异步场景）
 * 💡 本质：这是一个 线程级的全局变量容器，但比真正的全局变量更安全（线程隔离）。
 */
public class LoginContextHolder {

    private static final InheritableThreadLocal<Map<String, Object>> THREAD_LOCAL
            = new InheritableThreadLocal<>();

    // 存
    public static void set(String key, Object val) {
        Map<String, Object> map = getThreadLocalMap();
        map.put(key, val);
    }

    // 取
    public static Object get(String key){
        Map<String, Object> threadLocalMap = getThreadLocalMap();
        return threadLocalMap.get(key);
    }

    public static String getLoginId(){
        return (String) getThreadLocalMap().get("loginId");
    }

    // 清理（防内存泄漏）
    public static void remove(){
        THREAD_LOCAL.remove();
    }

    public static Map<String, Object> getThreadLocalMap() {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (Objects.isNull(map)) {
            // 线程安全：内部使用 ConcurrentHashMap
            map = new ConcurrentHashMap<>();
            THREAD_LOCAL.set(map);
        }
        return map;
    }


}
