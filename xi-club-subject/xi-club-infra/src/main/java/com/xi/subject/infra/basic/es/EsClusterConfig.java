package com.xi.subject.infra.basic.es;

import lombok.Data;

import java.io.Serializable;

/**
 * es集群类
 * 这部分负责读取配置文件（如 application.yml），建立与 ES 集群的连接。
 * EsClusterConfig.java (ES 集群配置类)
 * 作用：定义一个 ES 集群的基本信息。
 * 包含字段：name (集群名), nodes (节点地址，如 127.0.0.1:9200)。
 * 类比：就像数据库连接字符串的实体类。
 * @author: ChickenWing
 * @date: 2023/12/17
 */
@Data
public class EsClusterConfig implements Serializable {

    /**
     * 集群名称
     */
    private String name;

    /**
     * 集群节点
     */
    private String nodes;

}
