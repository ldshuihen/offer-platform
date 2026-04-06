package com.xi.circle.server.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xi.circle.api.enums.IsDeletedFlagEnum;
import com.xi.circle.api.req.GetShareCommentReq;
import com.xi.circle.api.req.RemoveShareCommentReq;
import com.xi.circle.api.req.SaveShareCommentReplyReq;
import com.xi.circle.api.vo.ShareCommentReplyVO;
import com.jingdianjichi.circle.server.dao.ShareCommentReplyMapper;
import com.jingdianjichi.circle.server.dao.ShareMomentMapper;
import com.jingdianjichi.circle.server.entity.dto.UserInfo;
import com.jingdianjichi.circle.server.entity.po.ShareCommentReply;
import com.jingdianjichi.circle.server.entity.po.ShareMoment;
import com.jingdianjichi.circle.server.rpc.UserRpc;
import com.jingdianjichi.circle.server.service.ShareCommentReplyService;
import com.jingdianjichi.circle.server.util.LoginUtil;
import com.jingdianjichi.circle.server.util.TreeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 评论及回复信息 服务实现类
 * </p>
 * 这段代码是圈子 / 动态类系统中 “评论及回复” 功能的核心服务实现，基于 MyBatis-Plus 封装，
 * 主要完成动态评论的新增、删除、查询三大核心业务，整体属于服务层（Service）实现类，对应 ShareCommentReplyService 接口。
 *
 * @author ChickenWing
 * @since 2024/05/16
 */
@Service
public class ShareCommentReplyServiceImpl extends ServiceImpl<ShareCommentReplyMapper, ShareCommentReply> implements ShareCommentReplyService {

    @Resource
    private ShareMomentMapper shareMomentMapper;

    @Resource
    private ShareCommentReplyMapper shareCommentReplyMapper;

    @Resource
    private UserRpc userRpc;

    /**
     * 1. 新增评论 / 回复（saveComment 方法）
     * 业务场景：支持对动态（ShareMoment）直接评论（回复类型 1），或对已有评论进行二级回复（回复类型 2）。
     * 核心逻辑：
     * 区分 “直接评论” 和 “回复评论” 两种场景，分别设置父 ID、目标用户、是否为作者回复等字段；
     * 关联动态表（ShareMoment），新增评论后更新动态的回复数（+1）；
     * 处理评论内容、图片列表（JSON 序列化存储），并记录创建人、创建时间、删除标识等基础字段；
     * 标记 “是否为作者回复”（区分回复动态作者 / 评论作者的场景）。
     * 事务保障：添加 @Transactional，确保评论新增和动态回复数更新原子性。
     * @param req
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveComment(SaveShareCommentReplyReq req) {
        ShareMoment moment = shareMomentMapper.selectById(req.getMomentId());
        ShareCommentReply comment = new ShareCommentReply();
        comment.setMomentId(req.getMomentId());
        comment.setReplyType(req.getReplyType());
        String loginId = LoginUtil.getLoginId();
        // 1评论 2回复
        if (req.getReplyType() == 1) {
            comment.setParentId(-1L);
            comment.setToId(req.getTargetId());
            comment.setToUser(loginId);
            comment.setToUserAuthor(Objects.nonNull(moment.getCreatedBy()) && loginId.equals(moment.getCreatedBy()) ? 1 : 0);
        } else {
            comment.setParentId(req.getTargetId());
            comment.setReplyId(req.getTargetId());
            comment.setReplyUser(loginId);
            comment.setReplayAuthor(Objects.nonNull(moment.getCreatedBy()) && loginId.equals(moment.getCreatedBy()) ? 1 : 0);

            //查replyId对应的内容
            ShareCommentReply shareCommentReply = shareCommentReplyMapper.selectById(req.getTargetId());
            comment.setToId(req.getTargetId());
            comment.setToUser(shareCommentReply.getCreatedBy());
            comment.setToUserAuthor(Objects.nonNull(shareCommentReply.getCreatedBy()) && loginId.equals(shareCommentReply.getCreatedBy()) ? 1 : 0);
        }
        comment.setContent(req.getContent());
        if (!CollectionUtils.isEmpty(req.getPicUrlList())) {
            comment.setPicUrls(JSON.toJSONString(req.getPicUrlList()));
        }
        comment.setCreatedBy(LoginUtil.getLoginId());
        comment.setCreatedTime(new Date());
        comment.setIsDeleted(IsDeletedFlagEnum.UN_DELETED.getCode());
        shareMomentMapper.incrReplyCount(moment.getId(), 1);
        return super.save(comment);
    }

    /**
     * 2. 删除评论 / 回复（removeComment 方法）
     * 业务场景：删除指定评论，同时级联删除其所有子回复，并更新动态的回复数。
     * 核心逻辑：
     * 先查询待删除评论的基础信息，再查询该动态下所有未删除的评论 / 回复；
     * 通过 TreeUtils 构建评论树形结构，递归找出待删除评论的所有子回复；
     * 批量标记这些评论 / 回复为 “已删除”（逻辑删除，而非物理删除）；
     * 同步更新动态表的回复数（减去删除的评论总数）。
     * 事务保障：确保逻辑删除和回复数更新原子性，避免数据不一致。
     * @param req
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean removeComment(RemoveShareCommentReq req) {
        ShareCommentReply comment = getById(req.getId());
        LambdaQueryWrapper<ShareCommentReply> query = Wrappers.<ShareCommentReply>lambdaQuery()
                .eq(ShareCommentReply::getMomentId, comment.getMomentId())
                .eq(ShareCommentReply::getIsDeleted, IsDeletedFlagEnum.UN_DELETED.getCode()).select(ShareCommentReply::getId,
                        ShareCommentReply::getMomentId,
                        ShareCommentReply::getReplyType,
                        ShareCommentReply::getContent,
                        ShareCommentReply::getPicUrls,
                        ShareCommentReply::getCreatedBy,
                        ShareCommentReply::getToUser,
                        ShareCommentReply::getParentId);
        List<ShareCommentReply> list = list(query);
        List<ShareCommentReply> replyList = new ArrayList<>();
        List<ShareCommentReply> tree = TreeUtils.buildTree(list);
        for (ShareCommentReply reply : tree) {
            TreeUtils.findAll(replyList, reply, req.getId());
        }
        // 关联子级对象及 moment 的回复数量
        Set<Long> ids = replyList.stream().map(ShareCommentReply::getId).collect(Collectors.toSet());
        LambdaUpdateWrapper<ShareCommentReply> update = Wrappers.<ShareCommentReply>lambdaUpdate()
                .eq(ShareCommentReply::getMomentId, comment.getMomentId())
                .in(ShareCommentReply::getId, ids);
        ShareCommentReply updateEntity = new ShareCommentReply();
        updateEntity.setIsDeleted(IsDeletedFlagEnum.DELETED.getCode());
        int count = getBaseMapper().update(updateEntity, update);
        shareMomentMapper.incrReplyCount(comment.getMomentId(), -count);
        return true;
    }

    /**
     * 3. 查询评论 / 回复列表（listComment 方法）
     * 业务场景：查询指定动态下的所有未删除评论 / 回复，组装成树形结构返回（支持层级展示）。
     * 核心逻辑：
     * 按动态 ID 筛选未删除的评论 / 回复，仅查询展示所需字段（减少数据传输）；
     * 批量获取评论创建人、被回复人的用户信息（通过 UserRpc 远程调用用户服务）；
     * 将数据库实体（ShareCommentReply）转换为前端展示 VO（ShareCommentReplyVO），处理图片列表（JSON 反序列化）、时间戳转换、用户昵称 / 头像填充；
     * 通过 TreeUtils 构建树形结构，让回复嵌套在对应父评论下，适配前端层级展示。
     * @param req
     * @return
     */
    @Override
    public List<ShareCommentReplyVO> listComment(GetShareCommentReq req) {
        LambdaQueryWrapper<ShareCommentReply> query = Wrappers.<ShareCommentReply>lambdaQuery()
                .eq(ShareCommentReply::getMomentId, req.getId())
                .eq(ShareCommentReply::getIsDeleted, IsDeletedFlagEnum.UN_DELETED.getCode())
                .select(ShareCommentReply::getId,
                        ShareCommentReply::getMomentId,
                        ShareCommentReply::getReplyType,
                        ShareCommentReply::getContent,
                        ShareCommentReply::getPicUrls,
                        ShareCommentReply::getCreatedBy,
                        ShareCommentReply::getToUser,
                        ShareCommentReply::getCreatedTime,
                        ShareCommentReply::getParentId);
        List<ShareCommentReply> list = list(query);
        List<String> userNameList = list.stream().map(ShareCommentReply::getCreatedBy).distinct().collect(Collectors.toList());
        List<String> toUserNameList = list.stream().map(ShareCommentReply::getToUser).distinct().collect(Collectors.toList());
        userNameList.addAll(toUserNameList);
        Map<String, UserInfo> userInfoMap = userRpc.batchGetUserInfo(userNameList);
        UserInfo defaultUser = new UserInfo();
        List<ShareCommentReplyVO> voList = list.stream().map(item -> {
            ShareCommentReplyVO vo = new ShareCommentReplyVO();
            vo.setId(item.getId());
            vo.setMomentId(item.getMomentId());
            vo.setReplyType(item.getReplyType());
            vo.setContent(item.getContent());
            if (Objects.nonNull(item.getPicUrls())) {
                vo.setPicUrlList(JSONArray.parseArray(item.getPicUrls(), String.class));
            }
            if (item.getReplyType() == 2) {
                vo.setFromId(item.getCreatedBy());
                vo.setToId(item.getToUser());
            }
            vo.setParentId(item.getParentId());
            UserInfo user = userInfoMap.getOrDefault(item.getCreatedBy(), defaultUser);
            vo.setUserName(user.getNickName());
            vo.setAvatar(user.getAvatar());
            vo.setCreatedTime(item.getCreatedTime().getTime());
            if (StringUtils.isNotBlank(item.getToUser())) {
                UserInfo toUser = userInfoMap.getOrDefault(item.getToUser(), defaultUser);
                vo.setToAvatar(toUser.getAvatar());
                vo.setToName(toUser.getNickName());
            }

            return vo;
        }).collect(Collectors.toList());
        return TreeUtils.buildTree(voList);
    }

}
