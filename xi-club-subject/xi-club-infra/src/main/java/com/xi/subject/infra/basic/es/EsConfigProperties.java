package com.xi.subject.infra.basic.es;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * EsConfigProperties.java (配置属性读取类)
 * 作用：Spring Boot 的 @ConfigurationProperties 注解类。它会自动读取项目配置文件中以 es.cluster 开头的配置，并将其映射为 EsClusterConfig 对象的列表。
 * 关键点：它负责把配置文件里的文字变成 Java 对象。
 */
@Component
@ConfigurationProperties(prefix = "es.cluster")
public class EsConfigProperties {

    private List<EsClusterConfig> esConfigs = new ArrayList<>();

    public List<EsClusterConfig> getEsConfigs() {
        return esConfigs;
    }

    public void setEsConfigs(List<EsClusterConfig> esConfigs) {
        this.esConfigs = esConfigs;
    }
}
