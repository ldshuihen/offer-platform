package com.xi.oss.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * minio配置管理
 *
 * 读取 MinIO 的连接参数（URL、AccessKey、SecretKey）。
 * 构建并注册 MinioClient Bean，供其他组件（如 MinioUtil 或 MinioStorageAdapter）注入使用。
 * 这是 MinIO SDK 的初始化入口，属于基础设施配置。
 *
 * 启动流程：
 * 应用启动时，MinioConfig 创建 MinioClient Bean。
 * StorageConfig 读取 storage.service.type=minio，创建 MinioStorageAdapter Bean。
 * 业务代码注入 StorageAdapter，实际获得的是 MinioStorageAdapter。
 * MinioStorageAdapter 通过 MinioUtil（内部使用 MinioClient）操作 MinIO。
 *
 *
 * @author: ChickenWing
 * @date: 2023/10/11
 */
@Configuration
public class MinioConfig {

    /**
     * minioUrl
     */
    @Value("${minio.url}")
    private String url;

    /**
     * minio账户
     */
    @Value("${minio.accessKey}")
    private String accessKey;

    /**
     * minio密码
     */
    @Value("${minio.secretKey}")
    private String secretKey;

    /**
     * 构造minioClient
     */
    @Bean
    public MinioClient getMinioClient() {
        return MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).build();
    }

}
