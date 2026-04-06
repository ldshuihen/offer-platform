package com.xi.subject.infra.basic.es;

import lombok.Data;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;

/**
 * 这部分专门针对复杂的搜索场景。
 * EsSearchRequest.java (搜索请求封装类)
 * 作用：封装复杂的搜索条件。
 * 包含内容：
 * bq：布尔查询构建器（用于组合 must, should, filter 等条件）。
 * fields：需要返回的字段。
 * from/size：分页参数。
 * sortName/sortOrder：排序规则。
 * highlightBuilder：高亮设置。
 * 用途：作为复杂查询方法的入参，避免方法参数过多。
 */
@Data
public class EsSearchRequest {

    /**
     * 查询条件
     */
    private BoolQueryBuilder bq;

    /**
     * 查询字段
     */
    private String[] fields;

    /**
     * 页数
     */
    private int from;

    /**
     * 条数
     */
    private int size;

    /**
     * 需要快照
     */
    private Boolean needScroll;

    /**
     * 快照缓存时间
     */
    private Long minutes;

    /**
     * 排序字段
     */
    private String sortName;

    /**
     * 排序类型
     */
    private SortOrder sortOrder;

    /**
     * 高亮builder
     */
    private HighlightBuilder highlightBuilder;

}
