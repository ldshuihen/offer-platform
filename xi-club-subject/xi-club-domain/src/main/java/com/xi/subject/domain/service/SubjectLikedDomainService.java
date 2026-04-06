package com.xi.subject.domain.service;


import com.xi.subject.common.entity.PageResult;
import com.xi.subject.domain.entity.SubjectLikedBO;

/**
 * 题目点赞表 领域service
 *
 * @author jingdianjichi
 * @since 2024-01-07 23:08:45
 */
public interface SubjectLikedDomainService {

    /**
     * 添加 题目点赞表 信息
     */
    void add(SubjectLikedBO subjectLikedBO);

    /**
     * 获取当前是否被点赞过
     */
    Boolean isLiked(String subjectId, String userId);

    /**
     * 获取点赞数量
     */
    Integer getLikedCount(String subjectId);

    /**
     * 更新 题目点赞表 信息
     */
    Boolean update(SubjectLikedBO subjectLikedBO);

    /**
     * 删除 题目点赞表 信息
     */
    Boolean delete(SubjectLikedBO subjectLikedBO);

    /**
     * 同步点赞数据，使用 xxl-job定时任务
     */
    void syncLiked();

    PageResult<SubjectLikedBO> getSubjectLikedPage(SubjectLikedBO subjectLikedBO);

    /**
     * 使用 RocketMQ 监听处理点赞逻辑
     * @param subjectLikedBO
     */
    void syncLikedByMsg(SubjectLikedBO subjectLikedBO);

}
