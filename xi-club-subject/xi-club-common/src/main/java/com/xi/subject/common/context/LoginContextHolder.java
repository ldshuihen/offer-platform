package com.xi.subject.common.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录上下文对象
 *一个典型的 基于 InheritableThreadLocal 实现的登录上下文工具类，用于在 单次请求的整个调用链中传递用户身份信息（如 loginId），
 * 而无需显式地将用户信息作为参数层层传递。
 * ✅ 一、核心作用
 * 在同一线程（及子线程）内，全局共享当前登录用户信息。
 * 典型使用场景：
 * 用户登录后，将 loginId 存入上下文
 * 后续的 Controller → Service → DAO 层均可直接获取 loginId
 * 避免在每个方法参数中手动传递 userId
 *✅ 总结
 * LoginContextHolder 的作用是：
 * 利用 InheritableThreadLocal 在单次请求的线程上下文中透传用户身份信息，实现“无感”获取当前登录用户，提升代码简洁性与可维护性。
 * ✅ 正确使用三要素：
 * 请求开始时：从 Token/Session 中解析 loginId 并 set
 * 业务代码中：通过 getLoginId() 获取
 * 请求结束时：必须调用 remove() 清理
 * 🔑 记住：
 * “ThreadLocal 不清理，线上必出事！” —— 这是无数生产事故换来的教训。
 * @author: ChickenWing
 * @date: 2023/11/26
 */
public class LoginContextHolder {

    // 1. 存储结构
    // 使用 InheritableThreadLocal 而非普通 ThreadLocal
    //优势：当主线程创建子线程时，子线程自动继承父线程的上下文数据（对线程池中的任务无效，见下文⚠️）
    private static final InheritableThreadLocal<Map<String, Object>> THREAD_LOCAL
            = new InheritableThreadLocal<>();


    public static void set(String key, Object val) {
        Map<String, Object> map = getThreadLocalMap();
        map.put(key, val);
    }

    public static Object get(String key){
        Map<String, Object> threadLocalMap = getThreadLocalMap();
        return threadLocalMap.get(key);
    }

    public static String getLoginId(){
        return (String) getThreadLocalMap().get("loginId");
    }

    public static void remove(){
        THREAD_LOCAL.remove();
    }

    // 2. 懒加载 Map
    // 首次访问时初始化 Map，避免空指针
    //使用 ConcurrentHashMap 保证多线程安全（虽然通常单线程访问，但防御性编程）
    public static Map<String, Object> getThreadLocalMap() {
        Map<String, Object> map = THREAD_LOCAL.get();
        if (Objects.isNull(map)) {
            map = new ConcurrentHashMap<>();// // 线程安全
            THREAD_LOCAL.set(map);
        }
        return map;
    }


}
