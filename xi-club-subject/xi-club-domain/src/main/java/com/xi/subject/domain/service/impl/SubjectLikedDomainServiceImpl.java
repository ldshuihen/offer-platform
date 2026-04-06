package com.xi.subject.domain.service.impl;

import com.alibaba.fastjson.JSON;
import com.xi.subject.common.entity.PageResult;
import com.xi.subject.common.enums.IsDeletedFlagEnum;
import com.xi.subject.common.enums.SubjectLikedStatusEnum;
import com.xi.subject.common.util.LoginUtil;
import com.xi.subject.domain.convert.SubjectLikedBOConverter;
import com.xi.subject.domain.entity.SubjectLikedBO;
import com.xi.subject.domain.entity.SubjectLikedMessage;
import com.xi.subject.domain.redis.RedisUtil;
import com.xi.subject.domain.service.SubjectLikedDomainService;
import com.xi.subject.infra.basic.entity.SubjectInfo;
import com.xi.subject.infra.basic.entity.SubjectLiked;
import com.xi.subject.infra.basic.service.SubjectInfoService;
import com.xi.subject.infra.basic.service.SubjectLikedService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 题目点赞表 领域service实现了
 *
 * @author jingdianjichi
 * @since 2024-01-07 23:08:45
 */
@Service
@Slf4j
public class SubjectLikedDomainServiceImpl implements SubjectLikedDomainService {

    @Resource
    private SubjectLikedService subjectLikedService;

    @Resource
    private SubjectInfoService subjectInfoService;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    private static final String SUBJECT_LIKED_KEY = "subject.liked";

    private static final String SUBJECT_LIKED_COUNT_KEY = "subject.liked.count";

    private static final String SUBJECT_LIKED_DETAIL_KEY = "subject.liked.detail";

    /**
     * 新增或更新题目点赞状态
     * 该方法处理用户对题目的点赞或取消点赞操作。它采用“异步落库”策略，优先更新高性能缓存（Redis）
     * 并发送消息通知，随后立即返回响应，从而极大提升接口吞吐量，保证高并发场景下的系统稳定性。
     *
     * @param subjectLikedBO 业务对象，包含以下关键字段：
     *                       - subjectId: 题目ID
     *                       - likeUserId: 点赞用户ID
     *                       - status: 点赞状态（1:点赞, 0:取消点赞）
     *
     * <p><b>处理流程：</b>
     * <ol>
     *   <li><b>消息通知</b>：构建点赞消息体，发送至RocketMQ。由消费者({@link #syncLikedByMsg})异步处理数据库持久化。</li>
     *   <li><b>Redis状态更新</b>：根据点赞状态实时更新Redis中的数据，包含两个部分：
     *     <ul>
     *       <li><b>详情标记</b>：使用Set结构记录用户对该题目的具体状态（Key: subject.liked.detail.{subjectId}.{userId}）。</li>
     *       <li><b>计数统计</b>：使用Incr/Decr原子操作实时更新题目的总点赞数（Key: subject.liked.count.{subjectId}）。</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p><b>设计亮点：</b>
     * 接口响应时间几乎等于0，因为真正的数据库写操作被移至MQ消费者中异步执行。
     * 即使数据库出现瞬时压力，也不会直接影响前端用户的点赞体验。
     */
    @Override
    public void add(SubjectLikedBO subjectLikedBO) {
        Long subjectId = subjectLikedBO.getSubjectId();
        String likeUserId = subjectLikedBO.getLikeUserId();
        Integer status = subjectLikedBO.getStatus();
//        String hashKey = buildSubjectLikedKey(subjectId.toString(), likeUserId);
//        redisUtil.putHash(SUBJECT_LIKED_KEY, hashKey, status);

        // 1. 发送MQ消息，通知异步落库
        // 这一步解耦了核心业务逻辑与数据库写入，是性能优化的关键
        SubjectLikedMessage subjectLikedMessage = new SubjectLikedMessage();
        subjectLikedMessage.setSubjectId(subjectId);
        subjectLikedMessage.setLikeUserId(likeUserId);
        subjectLikedMessage.setStatus(status);
        rocketMQTemplate.convertAndSend("subject-liked", JSON.toJSONString(subjectLikedMessage));


        // 2. 构建Redis Key
        // 使用点号分隔的Key结构，便于管理与查询
        String detailKey = SUBJECT_LIKED_DETAIL_KEY + "." + subjectId + "." + likeUserId;
        String countKey = SUBJECT_LIKED_COUNT_KEY + "." + subjectId;
        if (SubjectLikedStatusEnum.LIKED.getCode() == status) {
            // 3.1 如果是点赞操作：计数+1，并设置状态标记
            redisUtil.increment(countKey, 1);
            redisUtil.set(detailKey, "1");
        } else {
            // 3.2 如果是取消点赞操作：先检查计数，再进行-1操作并删除状态标记
            Integer count = redisUtil.getInt(countKey);
            if (Objects.isNull(count) || count <= 0) {
                return;
            }
            redisUtil.increment(countKey, -1);
            redisUtil.del(detailKey);
        }
    }

    @Override
    public Boolean isLiked(String subjectId, String userId) {
        String detailKey = SUBJECT_LIKED_DETAIL_KEY + "." + subjectId + "." + userId;
        return redisUtil.exist(detailKey);
    }

    @Override
    public Integer getLikedCount(String subjectId) {
        String countKey = SUBJECT_LIKED_COUNT_KEY + "." + subjectId;
        Integer count = redisUtil.getInt(countKey);
        if (Objects.isNull(count) || count <= 0) {
            return 0;
        }
        return redisUtil.getInt(countKey);
    }

    private String buildSubjectLikedKey(String subjectId, String userId) {
        return subjectId + ":" + userId;
    }

    @Override
    public Boolean update(SubjectLikedBO subjectLikedBO) {
        SubjectLiked subjectLiked = SubjectLikedBOConverter.INSTANCE.convertBOToEntity(subjectLikedBO);
        return subjectLikedService.update(subjectLiked) > 0;
    }

    @Override
    public Boolean delete(SubjectLikedBO subjectLikedBO) {
        SubjectLiked subjectLiked = new SubjectLiked();
        subjectLiked.setId(subjectLikedBO.getId());
        subjectLiked.setIsDeleted(IsDeletedFlagEnum.DELETED.getCode());
        return subjectLikedService.update(subjectLiked) > 0;
    }

    /**
     * 批量同步缓存中的点赞数据到数据库（定时任务触发）
     * 该方法主要用于定时将Redis Hash结构中累积的点赞操作批量持久化到MySQL，
     * 作为MQ异步落库的一种补充或兜底方案（或者在未使用MQ的旧逻辑中使用）。
     *
     * @throws Exception 如果Redis数据格式异常或数据库批量操作失败
     *
     * <p><b>处理流程：</b>
     * <ul>
     *   <li>1. 从Redis中获取指定Key的整个Hash映射表，并在获取后删除该Key（原子性操作，防止重复消费）</li>
     *   <li>2. 检查获取的数据是否为空，为空则直接返回</li>
     *   <li>3. 遍历Hash中的每一个Entry（键值对）</li>
     *   <li>4. 解析Key（格式通常为 "subjectId:userId"）和Value（状态）</li>
     *   <li>5. 将每一条记录转换为数据库实体对象，并加入列表</li>
     *   <li>6. 调用Service层进行批量插入或更新</li>
     * </ul>
     *
     * <p><b>关键细节：</b>
     * 此处使用了 redisUtil.getHashAndDelete，这是一个关键的原子性操作。
     * 先读取数据，确认写库成功后再删除缓存标记，或者在读取的同时清除缓存（取决于具体实现），
     * 这样可以避免因系统崩溃导致的数据重复入库问题。
     */
    @Override
    public void syncLiked() {
        // 1. 从Redis中获取点赞Hash表并删除原Key（防止重复处理）
        Map<Object, Object> subjectLikedMap = redisUtil.getHashAndDelete(SUBJECT_LIKED_KEY);
        if (log.isInfoEnabled()) {
            log.info("syncLiked.subjectLikedMap:{}", JSON.toJSONString(subjectLikedMap));
        }
        if (MapUtils.isEmpty(subjectLikedMap)) {
            return;
        }
        // 2. 批量同步到数据库
        List<SubjectLiked> subjectLikedList = new LinkedList<>();
        subjectLikedMap.forEach((key, val) -> {
            SubjectLiked subjectLiked = new SubjectLiked();
            // 3. 解析Key，获取题目ID和用户ID
            String[] keyArr = key.toString().split(":");
            String subjectId = keyArr[0];
            String likedUser = keyArr[1];
            subjectLiked.setSubjectId(Long.valueOf(subjectId));
            subjectLiked.setLikeUserId(likedUser);
            // 4. 设置状态和逻辑删除标志
            subjectLiked.setStatus(Integer.valueOf(val.toString()));
            subjectLiked.setIsDeleted(IsDeletedFlagEnum.UN_DELETED.getCode());
            subjectLikedList.add(subjectLiked);
        });
        // 5. 执行批量入库
        subjectLikedService.batchInsertOrUpdate(subjectLikedList);
    }

    /**
     * 分页查询当前用户的点赞记录列表
     * 该方法主要用于“我的点赞”页面，展示用户历史点赞的题目及其相关信息
     *
     * @param subjectLikedBO 包含分页参数（pageNo, pageSize）以及查询条件的入参对象
     *          注意：该BO中可能还包含其他筛选条件（如题目类型、时间范围等），但通常默认查询当前登录用户的数据
     * @return PageResult<SubjectLikedBO> 返回分页结果，包含点赞记录列表和总数
     *         records 中的每个元素包含：点赞的题目ID、题目名称、点赞时间、状态等信息
     *
     * @throws Exception 如果分页参数非法或数据库查询发生错误
     *
     * <p><b>处理流程：</b>
     * <ul>
     *   <li>1. 解析分页参数，计算数据库查询的起始位置 (start)</li>
     *   <li>2. 设置查询条件：将当前登录用户ID（从LoginUtil获取）设置到查询条件中，确保只能查自己的记录</li>
     *   <li>3. 调用DAO层进行分页查询，获取点赞关系列表</li>
     *   <li>4. 遍历结果，通过SubjectInfoService补全题目名称（因为点赞表通常只存题目ID）</li>
     *   <li>5. 组装并返回分页结果</li>
     * </ul>
     *
     * <p><b>性能注意：</b>
     * 此处存在 N+1 查询问题。如果一页显示 10 条记录，会循环 10 次调用 subjectInfoService.queryById。
     * 在高并发场景下，建议优化为批量查询（batchQueryById）以减少数据库交互次数。
     */
    @Override
    public PageResult<SubjectLikedBO> getSubjectLikedPage(SubjectLikedBO subjectLikedBO) {
        // 1.初始化分页结果对象
        PageResult<SubjectLikedBO> pageResult = new PageResult<>();
        pageResult.setPageNo(subjectLikedBO.getPageNo());
        pageResult.setPageSize(subjectLikedBO.getPageSize());
        // 2. 计算分页起始索引
        int start = (subjectLikedBO.getPageNo() - 1) * subjectLikedBO.getPageSize();
        // 转换BO为Entity， 用于数据库查询
        SubjectLiked subjectLiked = SubjectLikedBOConverter.INSTANCE.convertBOToEntity(subjectLikedBO);
        // 3.强制设置查询用户为当前登录用户，保证数据安全性（防止越权查询）
        subjectLiked.setLikeUserId(LoginUtil.getLoginId());
        // 4. 查询总记录条数
        int count = subjectLikedService.countByCondition(subjectLiked);
        if (count == 0) {
            return pageResult;
        }
        // 5.查询当前也的数据列表
        List<SubjectLiked> subjectLikedList = subjectLikedService.queryPage(subjectLiked, start,
                subjectLikedBO.getPageSize());
        // 6.转换Entity为BO
        List<SubjectLikedBO> subjectInfoBOS = SubjectLikedBOConverter.INSTANCE.convertListInfoToBO(subjectLikedList);
        // 7.补全题目名称（N+1查询，待优化）
        subjectInfoBOS.forEach(info -> {
            SubjectInfo subjectInfo = subjectInfoService.queryById(info.getSubjectId());
            info.setSubjectName(subjectInfo.getSubjectName());
        });
        // 8.组装分页结果
        pageResult.setRecords(subjectInfoBOS);
        pageResult.setTotal(count);
        return pageResult;
    }

    /**
     * 通过消息队列异步同步点赞数据到数据库
     * 该方法主要用于消费RocketMQ的消息，将Redis中累积的点赞操作持久化到MySQL。
     *
     * @param subjectLikedBO 包含点赞业务数据的领域对象，通常包含以下关键信息：
     *                       - subjectId: 题目ID
     *                       - likeUserId: 点赞用户ID
     *                       - status: 点赞状态（1:点赞, 0:取消点赞）
     *
     * @throws Exception 如果数据转换失败或数据库批量操作发生异常
     *
     * <p><b>处理流程：</b>
     * <ul>
     *   <li>1. 将传入的BO对象转换为数据库实体Entity（如有必要）</li>
     *   <li>2. 构建待插入/更新的实体列表</li>
     *   <li>3. 调用DAO层的批量插入或更新方法，实现数据落库</li>
     * </ul>
     *
     * <p><b>设计背景：</b>
     * 系统采用“异步落库”策略以提升性能。用户点赞时，数据先写入Redis并发送MQ消息，
     * 而不是直接同步写库。此方法作为MQ的消费者，负责在后台将这些变更同步到数据库，
     * 保证了最终一致性。
     */

    @Override
    public void syncLikedByMsg(SubjectLikedBO subjectLikedBO) {
        // 1. 创建用于批量操作的实体列表
        List<SubjectLiked> subjectLikedList = new LinkedList<>();

        // 2. 创建持久化实体对象
        SubjectLiked subjectLiked = new SubjectLiked();

        // 3. 复制属性（注意：此处对subjectId进行了Long类型的转换）
        subjectLiked.setSubjectId(Long.valueOf(subjectLikedBO.getSubjectId()));
        subjectLiked.setLikeUserId(subjectLikedBO.getLikeUserId());
        subjectLiked.setStatus(subjectLikedBO.getStatus());

        // 4. 设置逻辑删除状态为“未删除”
        subjectLiked.setIsDeleted(IsDeletedFlagEnum.UN_DELETED.getCode());

        // 5. 将实体加入列表
        subjectLikedList.add(subjectLiked);

        // 6. 执行批量插入或更新操作
        subjectLikedService.batchInsertOrUpdate(subjectLikedList);
    }

}
