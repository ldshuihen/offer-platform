package com.xi.subject.domain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池的config管理
 * ThreadPoolConfig.java —— 线程池 Bean 定义
 * ✅ 核心功能：
 * 通过 Spring @Configuration 注册一个名为 labelThreadPool 的 ThreadPoolExecutor Bean，供业务代码注入使用（如你之前提到的“分类标签并发查询”）。
 * 💡 为什么不用 Executors 工厂方法？
 * Executors.newFixedThreadPool 等使用无界队列（LinkedBlockingQueue 默认 Integer.MAX_VALUE），极易导致 OOM。此处显式指定有界队列，是安全做法。
 *
 * 关键原理：Spring 容器管理的是“Bean 实例”，而不是“类的来源”
 * ThreadPoolExecutor 是 JDK 的类 ✔️
 * 但你在 ThreadPoolConfig.java 中手动创建了一个 ThreadPoolExecutor 实例
 * 并通过 @Bean 将其注册为 Spring 容器中的一个 Bean，此时，Spring 容器中就有一个 名为 labelThreadPool 的 Bean，它的类型是 ThreadPoolExecutor。
 *
 * @Resource
 * private ThreadPoolExecutor labelThreadPool;
 * @Resource 的默认行为（JSR-250 标准）：
 * 优先按字段名（labelThreadPool）作为 Bean 名称查找
 * 找到名为 labelThreadPool 的 Bean → 注入成功
 * ✅ 这正是你在 @Bean("labelThreadPool") 中指定的名字！
 *
 * @Autowired
 * private ThreadPoolExecutor labelThreadPool;
 * @Autowired 默认按类型（byType）注入
 * 只要容器中只有一个 ThreadPoolExecutor 类型的 Bean，就能注入成功
 * 如果有多个，就需要配合 @Qualifier("labelThreadPool") 指定名称
 * @author: ChickenWing
 * @date: 2023/11/26
 */
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "labelThreadPool")
    public ThreadPoolExecutor getLabelThreadPool() {
        return new ThreadPoolExecutor(20, 100, 5,
                TimeUnit.SECONDS, new LinkedBlockingDeque<>(40),
                new CustomNameThreadFactory("label"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

}
