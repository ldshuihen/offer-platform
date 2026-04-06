package com.xi.subject.infra.basic.es;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * EsSourceData.java (ES 文档数据类)
 * 作用：封装要存入或读取的一条 ES 数据。
 * 结构：包含 docId (文档 ID) 和 data (真正的数据，是一个 Map<String, Object>，即字段名-字段值的键值对)。
 * 用途：作为插入（Insert）或更新（Update）操作的入参。
 */
@Data
public class EsSourceData implements Serializable {

    private String docId;

    private Map<String, Object> data;

}
