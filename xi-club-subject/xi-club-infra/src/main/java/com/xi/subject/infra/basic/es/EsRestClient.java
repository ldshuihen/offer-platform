package com.xi.subject.infra.basic.es;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.*;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.elasticsearch.search.sort.ScoreSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;

/**
 *EsRestClient.java (核心连接管理器)
 * 作用：这是整个工具箱的心脏。
 * 初始化：在 @PostConstruct 中读取配置，初始化 RestHighLevelClient 连接池（clientMap），支持连接多个不同的 ES 集群。
 * 生命周期：它维护了所有集群的连接实例，供后续操作调用。
 *
 *
 * 这份代码 EsRestClient.java 是一个典型的 Elasticsearch (ES) 客户端工具类。
 * 它的核心作用是封装 Elasticsearch 的 Java High Level REST Client API，为你的 Spring Boot 项目提供一套简单、统一、静态的接口，用于执行常见的数据操作（增删改查、批量处理、分词等）。
 * 简单来说，它充当了你的 Java 应用和 Elasticsearch 数据库之间的“翻译官”和“连接池”。
 *
 * 代码中定义了一系列 static 方法，供项目其他模块直接调用：
 * 表格
 * 功能分类	包含的方法	                                    说明
 * 单文档操作	insertDoc, updateDoc, deleteDoc, getDocById	    实现索引（Insert）、更新（Update）、删除（Delete）和获取（Get）单条数据的功能。
 * 批量操作	batchInsertDoc, batchUpdateDoc	                使用 ES 的 Bulk API，一次性处理多条数据，提高插入和更新的效率。
 * 查询操作	searchWithTermQuery	                            执行复杂的搜索请求。支持布尔查询(BoolQueryBuilder)、分页、排序、高亮和滚动查询(Scroll)。
 * 全量操作	delete, updateByQuery	                        delete方法会删除索引中所有数据（Match All）；updateByQuery 支持根据查询条件批量更新文档。
 * 辅助工具	isExistDocById, getAnalyze	                    检查文档是否存在；调用 ES 的分析器（代码中硬编码为 ik_smart）对中文文本进行分词测试。
 */
@Component
@Slf4j
public class EsRestClient {

    public static Map<String, RestHighLevelClient> clientMap = new HashMap<>();

    @Resource
    private EsConfigProperties esConfigProperties;

    private static final RequestOptions COMMON_OPTIONS;

    static {
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        COMMON_OPTIONS = builder.build();
    }

    @PostConstruct
    public void initialize() {
        List<EsClusterConfig> esConfigs = esConfigProperties.getEsConfigs();
        for (EsClusterConfig esConfig : esConfigs) {
            log.info("initialize.config.name:{},node:{}", esConfig.getName(), esConfig.getNodes());
            RestHighLevelClient restHighLevelClient = initRestClient(esConfig);
            if (restHighLevelClient != null) {
                clientMap.put(esConfig.getName(), restHighLevelClient);
            } else {
                log.error("config.name:{},node:{}.initError", esConfig.getName(), esConfig.getNodes());
            }
        }
    }

    private RestHighLevelClient initRestClient(EsClusterConfig esClusterConfig) {
        String[] ipPortArr = esClusterConfig.getNodes().split(",");
        List<HttpHost> httpHostList = new ArrayList<>(ipPortArr.length);
        for (String ipPort : ipPortArr) {
            String[] ipPortInfo = ipPort.split(":");
            if (ipPortInfo.length == 2) {
                HttpHost httpHost = new HttpHost(ipPortInfo[0], NumberUtils.toInt(ipPortInfo[1]));
                httpHostList.add(httpHost);
            }
        }
        HttpHost[] httpHosts = new HttpHost[httpHostList.size()];
        httpHostList.toArray(httpHosts);

        RestClientBuilder builder = RestClient.builder(httpHosts);
        RestHighLevelClient restHighLevelClient = new RestHighLevelClient(builder);
        return restHighLevelClient;
    }

    private static RestHighLevelClient getClient(String clusterName) {
        return clientMap.get(clusterName);
    }

    //当首次向 subject_index 写入文档时，ES 会根据写入字段的「Java 数据类型」+「JSON 序列化后的类型」自动推断并生成对应的 ES 字段类型，
    // 最终写入 Mapping；若索引已存在，则仅校验字段类型兼容性（如不允许 long 改为 text），兼容则直接写入文档，不修改现有 Mapping。
    // 所以，不需要事先创建索引
    public static boolean insertDoc(EsIndexInfo esIndexInfo, EsSourceData esSourceData) {
        try {
            // 1. 构建索引请求：指定要写入的索引名（来自EsIndexInfo，关联配置文件的集群-索引映射）
            IndexRequest indexRequest = new IndexRequest(esIndexInfo.getIndexName());

            // 2. 设置文档数据：EsSourceData的data是Map结构，对应ES文档的字段-值（如subject_name: "Java面试题"）
            indexRequest.source(esSourceData.getData());

            // 3. 指定文档ID：自定义业务主键（避免ES自动生成ID，方便后续精准更新/删除）
            indexRequest.id(esSourceData.getDocId());

            // 4. 执行插入：通过集群名获取对应RestHighLevelClient，发送请求
            //    clientMap在@PostConstruct初始化时，从EsConfigProperties读取配置构建
            getClient(esIndexInfo.getClusterName()).index(indexRequest, COMMON_OPTIONS);

            // 5. 插入成功返回true
            return true;
        } catch (Exception e) {
            // 6. 异常兜底：捕获所有异常（网络/权限/索引不存在等），打印日志后返回false
            log.error("insertDoc.exception:{}", e.getMessage(), e);
        }
        return false;
    }

    public static boolean updateDoc(EsIndexInfo esIndexInfo, EsSourceData esSourceData) {
        try {
            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.index(esIndexInfo.getIndexName());
            updateRequest.id(esSourceData.getDocId());
            updateRequest.doc(esSourceData.getData());
            getClient(esIndexInfo.getClusterName()).update(updateRequest, COMMON_OPTIONS);
            return true;
        } catch (Exception e) {
            log.error("updateDoc.exception:{}", e.getMessage(), e);
        }
        return false;
    }

    public static boolean batchUpdateDoc(EsIndexInfo esIndexInfo,
                                         List<EsSourceData> esSourceDataList) {
        try {
            boolean flag = false;
            BulkRequest bulkRequest = new BulkRequest();
            for (EsSourceData esSourceData : esSourceDataList) {
                String docId = esSourceData.getDocId();
                if (StringUtils.isNotBlank(docId)) {
                    UpdateRequest updateRequest = new UpdateRequest();
                    updateRequest.index(esIndexInfo.getIndexName());
                    updateRequest.id(esSourceData.getDocId());
                    updateRequest.doc(esSourceData.getData());
                    bulkRequest.add(updateRequest);
                    flag = true;
                }
            }

            if (flag) {
                BulkResponse bulk = getClient(esIndexInfo.getClusterName()).bulk(bulkRequest, COMMON_OPTIONS);
                if (bulk.hasFailures()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.error("batchUpdateDoc.exception:{}", e.getMessage(), e);
        }
        return false;
    }

    public static boolean delete(EsIndexInfo esIndexInfo) {
        try {
            DeleteByQueryRequest deleteByQueryRequest =
                    new DeleteByQueryRequest(esIndexInfo.getIndexName());
            deleteByQueryRequest.setQuery(QueryBuilders.matchAllQuery());
            BulkByScrollResponse response = getClient(esIndexInfo.getClusterName()).deleteByQuery(
                    deleteByQueryRequest, COMMON_OPTIONS
            );
            long deleted = response.getDeleted();
            log.info("deleted.size:{}", deleted);
            return true;
        } catch (Exception e) {
            log.error("delete.exception:{}", e.getMessage(), e);
        }
        return false;
    }

    public static boolean deleteDoc(EsIndexInfo esIndexInfo, String docId) {
        try {
            DeleteRequest deleteRequest = new DeleteRequest(esIndexInfo.getIndexName());
            deleteRequest.id(docId);
            DeleteResponse response = getClient(esIndexInfo.getClusterName()).delete(deleteRequest, COMMON_OPTIONS);
            log.info("deleteDoc.response:{}", JSON.toJSONString(response));
            return true;
        } catch (Exception e) {
            log.error("deleteDoc.exception:{}", e.getMessage(), e);
        }
        return false;
    }

    public static boolean isExistDocById(EsIndexInfo esIndexInfo, String docId) {
        try {
            GetRequest getRequest = new GetRequest(esIndexInfo.getIndexName());
            getRequest.id(docId);
            return getClient(esIndexInfo.getClusterName()).exists(getRequest, COMMON_OPTIONS);
        } catch (Exception e) {
            log.error("isExistDocById.exception:{}", e.getMessage(), e);
        }
        return false;
    }

    public static Map<String, Object> getDocById(EsIndexInfo esIndexInfo, String docId) {
        try {
            GetRequest getRequest = new GetRequest(esIndexInfo.getIndexName());
            getRequest.id(docId);
            GetResponse response = getClient(esIndexInfo.getClusterName()).get(getRequest, COMMON_OPTIONS);
            Map<String, Object> source = response.getSource();
            return source;
        } catch (Exception e) {
            log.error("isExistDocById.exception:{}", e.getMessage(), e);
        }
        return null;
    }

    public static Map<String, Object> getDocById(EsIndexInfo esIndexInfo, String docId,
                                                 String[] fields) {
        try {
            GetRequest getRequest = new GetRequest(esIndexInfo.getIndexName());
            getRequest.id(docId);
            FetchSourceContext fetchSourceContext = new FetchSourceContext(true, fields, null);
            getRequest.fetchSourceContext(fetchSourceContext);
            GetResponse response = getClient(esIndexInfo.getClusterName()).get(getRequest, COMMON_OPTIONS);
            Map<String, Object> source = response.getSource();
            return source;
        } catch (Exception e) {
            log.error("isExistDocById.exception:{}", e.getMessage(), e);
        }
        return null;
    }


    public static SearchResponse searchWithTermQuery(EsIndexInfo esIndexInfo,
                                                     EsSearchRequest esSearchRequest) {
        try {
            BoolQueryBuilder bq = esSearchRequest.getBq();
            String[] fields = esSearchRequest.getFields();
            int from = esSearchRequest.getFrom();
            int size = esSearchRequest.getSize();
            Long minutes = esSearchRequest.getMinutes();
            Boolean needScroll = esSearchRequest.getNeedScroll();
            String sortName = esSearchRequest.getSortName();
            SortOrder sortOrder = esSearchRequest.getSortOrder();

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(bq);
            searchSourceBuilder.fetchSource(fields, null).from(from).size(size);

            if (Objects.nonNull(esSearchRequest.getHighlightBuilder())) {
                searchSourceBuilder.highlighter(esSearchRequest.getHighlightBuilder());
            }

            if (StringUtils.isNotBlank(sortName)) {
                searchSourceBuilder.sort(sortName);
            }

            searchSourceBuilder.sort(new ScoreSortBuilder().order(SortOrder.DESC));

            SearchRequest searchRequest = new SearchRequest();
            searchRequest.searchType(SearchType.DEFAULT);
            searchRequest.indices(esIndexInfo.getIndexName());
            searchRequest.source(searchSourceBuilder);
            if (needScroll) {
                Scroll scroll = new Scroll(TimeValue.timeValueMinutes(minutes));
                searchRequest.scroll(scroll);
            }
            SearchResponse search = getClient(esIndexInfo.getClusterName()).search(searchRequest, COMMON_OPTIONS);
            return search;
        } catch (Exception e) {
            log.error("searchWithTermQuery.exception:{}", e.getMessage(), e);
        }
        return null;
    }

    public static boolean batchInsertDoc(EsIndexInfo esIndexInfo, List<EsSourceData> esSourceDataList) {
        if (log.isInfoEnabled()) {
            log.info("批量新增ES:" + esSourceDataList.size());
            log.info("indexName:" + esIndexInfo.getIndexName());
        }
        try {
            boolean flag = false;
            BulkRequest bulkRequest = new BulkRequest();

            for (EsSourceData source : esSourceDataList) {
                String docId = source.getDocId();
                if (StringUtils.isNotBlank(docId)) {
                    IndexRequest indexRequest = new IndexRequest(esIndexInfo.getIndexName());
                    indexRequest.id(docId);
                    indexRequest.source(source.getData());
                    bulkRequest.add(indexRequest);
                    flag = true;
                }
            }


            if (flag) {
                BulkResponse response = getClient(esIndexInfo.getClusterName()).bulk(bulkRequest, COMMON_OPTIONS);
                if (response.hasFailures()) {
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("batchInsertDoc.error", e);
        }

        return true;
    }

    public static boolean updateByQuery(EsIndexInfo esIndexInfo, QueryBuilder queryBuilder, Script script, int batchSize) {
        if (log.isInfoEnabled()) {
            log.info("updateByQuery.indexName:" + esIndexInfo.getIndexName());
        }
        try {
            UpdateByQueryRequest updateByQueryRequest = new UpdateByQueryRequest(esIndexInfo.getIndexName());
            updateByQueryRequest.setQuery(queryBuilder);
            updateByQueryRequest.setScript(script);
            updateByQueryRequest.setBatchSize(batchSize);
            updateByQueryRequest.setAbortOnVersionConflict(false);
            BulkByScrollResponse response = getClient(esIndexInfo.getClusterName()).updateByQuery(updateByQueryRequest, RequestOptions.DEFAULT);
            List<BulkItemResponse.Failure> failures = response.getBulkFailures();
        } catch (Exception e) {
            log.error("updateByQuery.error", e);
        }
        return true;
    }

    /**
     * 分词方法
     */
    public static List<String> getAnalyze(EsIndexInfo esIndexInfo, String text) throws Exception {
        List<String> list = new ArrayList<String>();
        Request request = new Request("GET", "_analyze");
        JSONObject entity = new JSONObject();
        entity.put("analyzer", "ik_smart");
        entity.put("text", text);
        request.setJsonEntity(entity.toJSONString());
        Response response = getClient(esIndexInfo.getClusterName()).getLowLevelClient().performRequest(request);
        JSONObject tokens = JSONObject.parseObject(EntityUtils.toString(response.getEntity()));
        JSONArray arrays = tokens.getJSONArray("tokens");
        for (int i = 0; i < arrays.size(); i++) {
            JSONObject obj = JSON.parseObject(arrays.getString(i));
            list.add(obj.getString("token"));
        }
        return list;
    }


}
