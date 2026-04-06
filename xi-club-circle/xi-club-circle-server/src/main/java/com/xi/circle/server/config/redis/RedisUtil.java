package com.jingdianjichi.circle.server.config.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 Redis工具类
 封装了常用的Redis操作方法，提供更简洁的接口用于缓存读写、ZSet操作等。
 * @author: ChickenWing
 @date: 2023/10/28
 */
@Component
@Slf4j
public class RedisUtil {

    /**
     注入Spring封装的RedisTemplate，用于执行具体的Redis操作
     */
    @Resource
    private RedisTemplate redisTemplate;

    /**
     定义缓存Key的分隔符，用于拼接多段式的Key（例如：module:submodule:keyId）
     */
    private static final String CACHE_KEY_SEPARATOR = ".";

    /**
     构建缓存Key
     将传入的多个字符串参数使用分隔符拼接成一个完整的Redis Key
     * @param strObjs 可变参数，表示Key的各个部分
     @return 拼接后的完整Key字符串
     */
    public String buildKey(String... strObjs) {
        return Stream.of(strObjs).collect(Collectors.joining(CACHE_KEY_SEPARATOR));
    }

    /**
     判断Key是否存在
     * @param key 缓存Key
     @return 如果Key存在返回true，否则返回false
     */
    public boolean exist(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     删除指定的Key
     * @param key 缓存Key
     @return 如果删除成功返回true，Key不存在或删除失败返回false
     */
    public boolean del(String key) {
        return redisTemplate.delete(key);
    }

    /**
     设置字符串类型的值（不带过期时间）
     * @param key   缓存Key
     @param value 缓存值
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     设置字符串类型的值（带过期时间）
     使用setIfAbsent实现，仅当Key不存在时才设置
     * @param key     缓存Key
     @param value   缓存值
     @param time    过期时间数值
     @param timeUnit 过期时间单位（如TimeUnit.SECONDS）
     @return 设置成功返回true，Key已存在返回false
     */
    public boolean setNx(String key, String value, Long time, TimeUnit timeUnit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, time, timeUnit);
    }

    /**
     获取字符串类型的缓存值
     * @param key 缓存Key
     @return 缓存的值，如果Key不存在则返回null
     */
    public String get(String key) {
        return (String) redisTemplate.opsForValue().get(key);
    }

    /**
     向有序集合(ZSet)中添加元素
     * @param key    ZSet的Key
     @param value  存入的值
     @param score  分值
     @return 添加成功返回true，失败返回false
     */
    public Boolean zAdd(String key, String value, Long score) {
        return redisTemplate.opsForZSet().add(key, value, Double.valueOf(String.valueOf(score)));
    }

    /**
     获取有序集合(ZSet)的元素总数
     * @param key ZSet的Key
     @return 集合中元素的数量
     */
    public Long countZset(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     获取有序集合(ZSet)中指定区间内的元素（按分数升序）
     * @param key   ZSet的Key
     @param start 起始索引（0为第一个元素）
     @param end   结束索引（-1为最后一个元素）
     @return 包含指定范围内元素的Set集合
     */
    public Set rangeZset(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     从有序集合(ZSet)中移除指定元素
     * @param key   ZSet的Key
     @param value 要移除的元素值
     @return 被成功移除的元素数量
     */
    public Long removeZset(String key, Object value) {
        return redisTemplate.opsForZSet().remove(key, value);
    }

    /**
     批量从有序集合(ZSet)中移除元素
     * @param key   ZSet的Key
     @param value 包含多个待移除元素的Set集合
     */
    public void removeZsetList(String key, Set value) {
        value.stream().forEach((val) -> redisTemplate.opsForZSet().remove(key, val));
    }

    /**
     获取有序集合(ZSet)中指定元素的分数
     * @param key   ZSet的Key
     @param value 元素值
     @return 该元素对应的分数
     */
    public Double score(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    /**
     获取有序集合(ZSet)中指定分数区间内的元素
     * @param key   ZSet的Key
     @param start 最小分数
     @param end   最大分数
     @return 包含指定分数范围内元素的Set集合
     */
    public Set rangeByScore(String key, long start, long end) {
        return redisTemplate.opsForZSet().rangeByScore(key, Double.valueOf(String.valueOf(start)), Double.valueOf(String.valueOf(end)));
    }

    /**
     为有序集合(ZSet)中的元素增加分数
     * @param key   ZSet的Key
     @param obj   元素值
     @param score 要增加的分数
     @return 增加分数后的新分数值
     */
    public Object addScore(String key, Object obj, double score) {
        return redisTemplate.opsForZSet().incrementScore(key, obj, score);
    }

    /**
     获取元素在有序集合(ZSet)中的排名（按分数升序，从0开始）
     * @param key ZSet的Key
     @param obj 元素值
     @return 该元素的排名位置
     */
    public Object rank(String key, Object obj) {
        return redisTemplate.opsForZSet().rank(key, obj);
    }
}