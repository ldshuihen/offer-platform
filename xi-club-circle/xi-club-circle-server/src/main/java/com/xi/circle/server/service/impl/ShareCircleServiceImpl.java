package com.jingdianjichi.circle.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xi.circle.api.enums.IsDeletedFlagEnum;
import com.xi.circle.api.req.RemoveShareCircleReq;
import com.xi.circle.api.req.SaveShareCircleReq;
import com.xi.circle.api.req.UpdateShareCircleReq;
import com.xi.circle.api.vo.ShareCircleVO;
import com.jingdianjichi.circle.server.dao.ShareCircleMapper;
import com.jingdianjichi.circle.server.entity.po.ShareCircle;
import com.jingdianjichi.circle.server.service.ShareCircleService;
import com.jingdianjichi.circle.server.util.LoginUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>
 * 圈子信息 服务实现类
 * </p>
 * 场景对比
 * 没有缓存时（每次调用 listResult）：
 * 查库：执行 SQL 从数据库拉取所有圈子数据。
 * 计算：使用 Java Stream 将扁平的列表（List）组装成树形结构（父子级联）。
 * 返回：给前端数据。
 * 缺点：数据库压力大，且 Stream 组装对象消耗 CPU。
 * 有缓存时（当前代码逻辑）：
 * 先查缓存：CACHE.getIfPresent(1)。
 * 命中则返回：如果缓存中有数据，直接跳过“查库”和“组装树”的步骤，直接返回结果。
 * 未命中再执行原逻辑：只有缓存失效或第一次访问时，才走完整的查库+组装流程，并将结果放入缓存。
 *
 * @author ChickenWing
 * @since 2024/05/16
 */
@Service
public class ShareCircleServiceImpl extends ServiceImpl<ShareCircleMapper, ShareCircle> implements ShareCircleService {

    private static final Cache<Integer, List<ShareCircleVO>> CACHE = Caffeine.newBuilder().initialCapacity(1)
            .maximumSize(1).expireAfterWrite(Duration.ofSeconds(30)).build();

    @Override
    public List<ShareCircleVO> listResult() {
        // 1.尝试从缓存获取
        List<ShareCircleVO> res = CACHE.getIfPresent(1);
        return Optional.ofNullable(res).orElseGet(() -> {
            // 2.缓存没有，执行这里的重逻辑
            List<ShareCircle> list = super.list(Wrappers.<ShareCircle>lambdaQuery().eq(ShareCircle::getIsDeleted, IsDeletedFlagEnum.UN_DELETED.getCode()));
            List<ShareCircle> parentList = list.stream().filter(item -> item.getParentId() == -1L).collect(Collectors.toList());
            Map<Long, List<ShareCircle>> map = list.stream().collect(Collectors.groupingBy(ShareCircle::getParentId));
            List<ShareCircleVO> collect = parentList.stream().map(item -> {
                ShareCircleVO vo = new ShareCircleVO();
                vo.setId(item.getId());
                vo.setCircleName(item.getCircleName());
                vo.setIcon(item.getIcon());
                List<ShareCircle> shareCircles = map.get(item.getId());
                if (CollectionUtils.isEmpty(shareCircles)) {
                    vo.setChildren(Collections.emptyList());
                } else {
                    List<ShareCircleVO> children = shareCircles.stream().map(cItem -> {
                        ShareCircleVO cVo = new ShareCircleVO();
                        cVo.setId(cItem.getId());
                        cVo.setCircleName(cItem.getCircleName());
                        cVo.setIcon(cItem.getIcon());
                        cVo.setChildren(Collections.emptyList());
                        return cVo;
                    }).collect(Collectors.toList());
                    vo.setChildren(children);
                }
                return vo;
            }).collect(Collectors.toList());
            // 3.把组装好的结果放回缓存
            CACHE.put(1, collect);
            return collect;
        });
    }

    @Override
    public Boolean saveCircle(SaveShareCircleReq req) {
        ShareCircle circle = new ShareCircle();
        circle.setCircleName(req.getCircleName());
        circle.setIcon(req.getIcon());
        circle.setParentId(req.getParentId());
        circle.setIsDeleted(IsDeletedFlagEnum.UN_DELETED.getCode());
        circle.setCreatedTime(new Date());
        circle.setCreatedBy(LoginUtil.getLoginId());
        CACHE.invalidateAll();
        return save(circle);
    }

    @Override
    public Boolean updateCircle(UpdateShareCircleReq req) {
        LambdaUpdateWrapper<ShareCircle> update = Wrappers.<ShareCircle>lambdaUpdate().eq(ShareCircle::getId, req.getId())
                .eq(ShareCircle::getIsDeleted, IsDeletedFlagEnum.UN_DELETED.getCode())
                .set(Objects.nonNull(req.getParentId()), ShareCircle::getParentId, req.getParentId())
                .set(Objects.nonNull(req.getIcon()), ShareCircle::getIcon, req.getIcon())
                .set(Objects.nonNull(req.getCircleName()), ShareCircle::getCircleName, req.getCircleName())
                .set(ShareCircle::getUpdateBy, LoginUtil.getLoginId())
                .set(ShareCircle::getUpdateTime, new Date());
        boolean res = super.update(update);
        CACHE.invalidateAll();
        return res;
    }

    @Override
    public Boolean removeCircle(RemoveShareCircleReq req) {
        boolean res = super.update(Wrappers.<ShareCircle>lambdaUpdate().eq(ShareCircle::getId, req.getId())
                .eq(ShareCircle::getIsDeleted, IsDeletedFlagEnum.UN_DELETED.getCode())
                .set(ShareCircle::getIsDeleted, IsDeletedFlagEnum.DELETED.getCode()));
        super.update(Wrappers.<ShareCircle>lambdaUpdate().eq(ShareCircle::getParentId, req.getId())
                .eq(ShareCircle::getIsDeleted, IsDeletedFlagEnum.UN_DELETED.getCode())
                .set(ShareCircle::getIsDeleted, IsDeletedFlagEnum.DELETED.getCode()));
        CACHE.invalidateAll();
        return res;
    }
}
