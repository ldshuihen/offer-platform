package com.xi.oss.config;

import com.xi.oss.adapter.StorageAdapter;
import com.xi.oss.adapter.AliStorageAdapter;
import com.xi.oss.adapter.MinioStorageAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储config
 *
 * 根据配置项 storage.service.type 动态决定使用哪种存储实现（MinIO 或 阿里云 OSS）。
 * 通过 @Bean 方法 注册一个 StorageAdapter 类型的 Spring Bean。
 * 使用 @RefreshScope 支持 运行时动态刷新配置（配合 Spring Cloud Config 或 Nacos 等配置中心）。
 * 📌 这是典型的 策略模式 + 工厂方法 的 Spring 实现，也是适配器模式的“装配点”。
 *
 * @author: ChickenWing
 * @date: 2023/10/14
 */
@Configuration
@RefreshScope
public class StorageConfig {

    @Value("${storage.service.type}")
    private String storageType;

    @Bean
    @RefreshScope
    public StorageAdapter storageService() {
        if ("minio".equals(storageType)) {
            return new MinioStorageAdapter();
        } else if ("aliyun".equals(storageType)) {
            return new AliStorageAdapter();
        } else {
            throw new IllegalArgumentException("未找到对应的文件存储处理器");
        }
    }

}
