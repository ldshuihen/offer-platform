package com.xi.subject.infra.basic.es;

import lombok.Data;

import java.io.Serializable;

/**
 * EsIndexInfo.java (索引信息类)
 * 作用：指定操作的目标位置。
 * 结构：包含 clusterName (去哪个集群) 和 indexName (操作哪个索引/表)。
 * 用途：告诉工具类“去哪台机器的哪个库里操作”。
 */
@Data
public class EsIndexInfo implements Serializable {

    /**
     * 集群名称
     */
    private String clusterName;

    /**
     * 索引名称
     */
    private String indexName;

}
