package com.xi.subject.application.controller;

import com.alibaba.fastjson.JSON;
import com.google.common.base.Preconditions;
import com.xi.subject.application.convert.SubjectLikedDTOConverter;
import com.xi.subject.application.dto.SubjectLikedDTO;
import com.xi.subject.common.entity.PageResult;
import com.xi.subject.common.entity.Result;
import com.xi.subject.common.util.LoginUtil;
import com.xi.subject.domain.entity.SubjectLikedBO;
import com.xi.subject.domain.service.SubjectLikedDomainService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 题目点赞表 controller
 *
 * @author jingdianjichi
 * @since 2024-01-07 23:08:45
 */
@RestController
@RequestMapping("/subjectLiked/")
@Slf4j
public class SubjectLikedController {

    @Resource
    private SubjectLikedDomainService subjectLikedDomainService;

    /**新增题目点赞表
     * 新增题目点赞记录
     * 该接口用于处理用户对题目的点赞或取消点赞操作。根据传入的状态值（1为点赞，0为取消点赞）
     * 更新Redis中的实时计数与状态，并通过RocketMQ异步通知数据库进行持久化。
     *
     * @param subjectLikedDTO 请求传输对象，包含以下关键字段：
     *                        - subjectId: 题目ID，不能为空
     *                        - status: 点赞状态，1表示点赞，0表示取消点赞，不能为空
     *                        - 其他审计字段（如创建人、时间等由系统自动填充）
     * @return Result<Boolean> 返回操作结果
     *         - 如果成功，返回 Result.ok(true)
     *         - 如果参数校验失败或系统异常，返回失败信息
     *
     * @throws IllegalArgumentException 如果题目ID或状态为空
     * @throws RuntimeException 如果服务调用出现异常
     *
     * <p><b>处理流程：</b>
     * <ul>
     *   <li>1. 日志记录：记录入参用于追踪</li>
     *   <li>2. 参数校验：使用 Preconditions.checkNotNull 校验必要字段</li>
     *   <li>3. 用户身份填充：从 LoginUtil 获取当前登录用户ID并设置到DTO中</li>
     *   <li>4. 对象转换：将 DTO 转换为领域对象 BO</li>
     *   <li>5. 领域服务调用：执行点赞核心逻辑（包含Redis更新与MQ发送）</li>
     *   <li>6. 返回成功结果</li>
     * </ul>
     *
     * <p><b>注意：</b>
     * 该接口采用“异步落库”策略。数据首先更新到Redis并发送MQ消息，
     * 并不直接同步写入数据库，以保证高并发下的响应速度。
     */
    @RequestMapping("add")
    public Result<Boolean> add(@RequestBody SubjectLikedDTO subjectLikedDTO) {

        try {
            if (log.isInfoEnabled()) {
                log.info("SubjectLikedController.add.dto:{}", JSON.toJSONString(subjectLikedDTO));
            }
            Preconditions.checkNotNull(subjectLikedDTO.getSubjectId(), "题目id不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getStatus(), "点赞状态不能为空");
            String loginId = LoginUtil.getLoginId();
            subjectLikedDTO.setLikeUserId(loginId);
            Preconditions.checkNotNull(subjectLikedDTO.getLikeUserId(), "点赞人不能为空");
            SubjectLikedBO SubjectLikedBO = SubjectLikedDTOConverter.INSTANCE.convertDTOToBO(subjectLikedDTO);
            subjectLikedDomainService.add(SubjectLikedBO);
            return Result.ok(true);
        } catch (Exception e) {
            log.error("SubjectLikedController.register.error:{}", e.getMessage(), e);
            return Result.fail("新增题目点赞表失败");
        }

    }


    /**
     * 查询我的点赞列表
     */
    @PostMapping("/getSubjectLikedPage")
    public Result<PageResult<SubjectLikedDTO>> getSubjectLikedPage(@RequestBody SubjectLikedDTO subjectLikedDTO) {
        try {
            if (log.isInfoEnabled()) {
                log.info("SubjectController.getSubjectLikedPage.dto:{}", JSON.toJSONString(subjectLikedDTO));
            }
            SubjectLikedBO subjectLikedBO = SubjectLikedDTOConverter.INSTANCE.convertDTOToBO(subjectLikedDTO);
            subjectLikedBO.setPageNo(subjectLikedDTO.getPageNo());
            subjectLikedBO.setPageSize(subjectLikedDTO.getPageSize());
            PageResult<SubjectLikedBO> boPageResult = subjectLikedDomainService.getSubjectLikedPage(subjectLikedBO);
            return Result.ok(boPageResult);
        } catch (Exception e) {
            log.error("SubjectCategoryController.getSubjectLikedPage.error:{}", e.getMessage(), e);
            return Result.fail("分页查询我的点赞失败");
        }
    }

    /**
     * 修改题目点赞表
     */
    @RequestMapping("update")
    public Result<Boolean> update(@RequestBody SubjectLikedDTO subjectLikedDTO) {

        try {
            if (log.isInfoEnabled()) {
                log.info("SubjectLikedController.update.dto:{}", JSON.toJSONString(subjectLikedDTO));
            }
            Preconditions.checkNotNull(subjectLikedDTO.getId(), "主键不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getSubjectId(), "题目id不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getLikeUserId(), "点赞人id不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getStatus(), "点赞状态 1点赞 0不点赞不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getCreatedBy(), "创建人不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getCreatedTime(), "创建时间不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getUpdateBy(), "修改人不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getUpdateTime(), "修改时间不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getIsDeleted(), "不能为空");
            SubjectLikedBO subjectLikedBO = SubjectLikedDTOConverter.INSTANCE.convertDTOToBO(subjectLikedDTO);
            return Result.ok(subjectLikedDomainService.update(subjectLikedBO));
        } catch (Exception e) {
            log.error("SubjectLikedController.update.error:{}", e.getMessage(), e);
            return Result.fail("更新题目点赞表信息失败");
        }

    }

    /**
     * 删除题目点赞表
     */
    @RequestMapping("delete")
    public Result<Boolean> delete(@RequestBody SubjectLikedDTO subjectLikedDTO) {

        try {
            if (log.isInfoEnabled()) {
                log.info("SubjectLikedController.delete.dto:{}", JSON.toJSONString(subjectLikedDTO));
            }
            Preconditions.checkNotNull(subjectLikedDTO.getId(), "主键不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getSubjectId(), "题目id不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getLikeUserId(), "点赞人id不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getStatus(), "点赞状态 1点赞 0不点赞不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getCreatedBy(), "创建人不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getCreatedTime(), "创建时间不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getUpdateBy(), "修改人不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getUpdateTime(), "修改时间不能为空");
            Preconditions.checkNotNull(subjectLikedDTO.getIsDeleted(), "不能为空");
            SubjectLikedBO subjectLikedBO = SubjectLikedDTOConverter.INSTANCE.convertDTOToBO(subjectLikedDTO);
            return Result.ok(subjectLikedDomainService.delete(subjectLikedBO));
        } catch (Exception e) {
            log.error("SubjectLikedController.delete.error:{}", e.getMessage(), e);
            return Result.fail("删除题目点赞表信息失败");
        }

    }

}
