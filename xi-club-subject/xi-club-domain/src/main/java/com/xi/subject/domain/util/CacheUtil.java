package com.xi.subject.domain.util;

import com.alibaba.fastjson.JSON;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存工具类
 * 定义了一个 基于 Guava Cache 的本地缓存工具类 CacheUtil，其主要作用是：
 * 在内存中缓存耗时或高频查询的结果（如分类数据、配置信息等），避免重复计算或数据库访问，提升系统性能。
 * @author: ChickenWing
 * @date: 2023/12/3
 */
@Component
public class CacheUtil<K, V> {

    // 1. 缓存存储结构,使用 Guava Cache 实现本地内存缓存（非分布式）
    private Cache<String, String> localCache =
            CacheBuilder.newBuilder()
                    .maximumSize(5000)
                    .expireAfterWrite(10, TimeUnit.SECONDS)
                    .build();

    // 2. 已实现的方法：getResult（缓存 List 数据）
    public List<V> getResult(String cacheKey, Class<V> clazz,
                             Function<String, List<V>> function) {
        List<V> resultList = new ArrayList<>();
        // 2.1. 尝试从缓存读取
        String content = localCache.getIfPresent(cacheKey);
        // 2.2. 命中缓存 → 反序列化返回
        if (StringUtils.isNotBlank(content)) {
            resultList = JSON.parseArray(content, clazz);
        } else {
            //2.3. 未命中 → 调用业务函数加载数据 → 写入缓存
            resultList = function.apply(cacheKey);
            if (!CollectionUtils.isEmpty(resultList)) {
                localCache.put(cacheKey, JSON.toJSONString(resultList));
            }
        }
        return resultList;
    }

    // 3. 未实现的方法：getMapResult（预留 Map 缓存）
    public Map<K, V> getMapResult(String cacheKey, Class<V> clazz,
                                  Function<String, Map<K, V>> function) {
        return new HashMap<>();
    }

}
