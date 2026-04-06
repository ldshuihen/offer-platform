package com.xi.interview.server.service.impl;

import com.xi.interview.api.enums.EngineEnum;
import com.xi.interview.api.req.InterviewSubmitReq;
import com.xi.interview.api.req.StartReq;
import com.xi.interview.api.vo.InterviewQuestionVO;
import com.xi.interview.api.vo.InterviewResultVO;
import com.xi.interview.api.vo.InterviewVO;
import com.xi.interview.server.dao.SubjectDao;
import com.xi.interview.server.entity.po.SubjectCategory;
import com.xi.interview.server.entity.po.SubjectInfo;
import com.xi.interview.server.entity.po.SubjectLabel;
import com.xi.interview.server.service.InterviewEngine;
import com.xi.interview.server.util.EvaluateUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自研面试引擎实现类（基于本地题库）
 * 核心功能：从本地数据库题库中读取面试题、标签、分类信息，完成面试题生成、答案评分、结果汇总
 * 区别于阿里云通义千问引擎（AlBLInterviewEngine），该引擎依赖本地存储的题库，而非调用大模型生成内容
 *
 * @author: 自研引擎开发组
 * @date: 202X-XX-XX
 */
@Service
public class JiChiInterviewEngine implements InterviewEngine {

    /**
     * 缓存所有面试题标签（初始化时从数据库加载，避免频繁查询）
     */
    private List<SubjectLabel> labels;

    /**
     * 缓存面试题分类映射（分类ID -> 分类对象），用于快速查询分类名称
     */
    private Map<Long, SubjectCategory> categoryMap;

    /**
     * 题库数据访问层，用于查询标签、分类、面试题等数据
     */
    @Resource
    private SubjectDao subjectDao;

    /**
     * 初始化方法（Bean初始化后执行）
     * 加载所有标签和分类数据到本地缓存，提升后续接口响应速度
     * @PostConstruct：Spring注解，在依赖注入完成后自动调用
     */
    @PostConstruct
    public void init() {
        // 加载所有标签数据到缓存
        labels = subjectDao.listAllLabel();
        // 加载所有分类数据，并转换为ID映射的Map（便于快速查找）
        categoryMap = subjectDao.listAllCategory()
                                .stream()
                                .collect(Collectors.toMap(SubjectCategory::getId, Function.identity()));
    }

    /**
     * 获取当前引擎类型
     * @return 引擎枚举（JI_CHI 标识自研引擎）
     */
    @Override
    public EngineEnum engineType() {
        return EngineEnum.JI_CHI;
    }

    /**
     * 面试题分析：根据输入的关键词匹配本地标签，返回标签+分类信息
     * 核心逻辑：过滤出包含输入关键词的标签，拼接分类+标签作为关键词，返回结构化数据
     *
     * @param KeyWords 前端传入的面试关键词列表（如"Java基础"、"SpringBoot"）
     * @return InterviewVO 包含匹配到的标签、分类ID、拼接后的关键词等信息
     */
    @Override
    public InterviewVO analyse(List<String> KeyWords) {
        // 入参校验：关键词为空时返回空对象，避免空指针
        if (CollectionUtils.isEmpty(KeyWords)) {
            return new InterviewVO();
        }

        // 过滤标签：只保留关键词列表中包含的标签，并转换为前端需要的VO对象
        List<InterviewVO.Interview> views = this.labels.stream()
                                                       // 过滤条件：标签名称在输入关键词列表中
                                                       .filter(item -> KeyWords.contains(item.getLabelName()))
                                                       // 转换为InterviewVO.Interview对象
                                                       .map(item -> {
                                                           InterviewVO.Interview interview = new InterviewVO.Interview();
                                                           // 根据标签关联的分类ID查询分类对象
                                                           SubjectCategory subjectCategory = categoryMap.get(item.getCategoryId());
                                                           // 拼接关键词：分类名称-标签名称（如"Java-集合"），无分类则仅保留标签名
                                                           if (Objects.nonNull(subjectCategory)) {
                                                               interview.setKeyWord(String.format("%s-%s", subjectCategory.getCategoryName(), item.getLabelName()));
                                                           } else {
                                                               interview.setKeyWord(item.getLabelName());
                                                           }
                                                           // 设置分类ID和标签ID（用于后续查询面试题）
                                                           interview.setCategoryId(item.getCategoryId());
                                                           interview.setLabelId(item.getId());
                                                           return interview;
                                                       })
                                                       .collect(Collectors.toList());

        // 封装返回结果
        InterviewVO vo = new InterviewVO();
        vo.setQuestionList(views);
        return vo;
    }

    /**
     * 生成面试题：根据标签ID从本地题库查询题目，随机选取8道返回
     * 核心逻辑：按标签ID查询题库 -> 转换为VO -> 超过8道则随机打乱并截取前8道
     *
     * @param req 包含标签ID列表的请求参数
     * @return InterviewQuestionVO 包含8道面试题（题目、答案、标签、分类等信息）
     */
    @Override
    public InterviewQuestionVO start(StartReq req) {
        // 提取请求中的标签ID列表（去重，避免重复查询）
        List<Long> ids = req.getQuestionList().stream()
                            .map(StartReq.Key::getLabelId)
                            .distinct()
                            .collect(Collectors.toList());

        // 标签ID为空时返回空对象
        if (CollectionUtils.isEmpty(ids)) {
            return new InterviewQuestionVO();
        }

        // 根据标签ID列表查询对应的面试题
        List<SubjectInfo> subjectInfos = subjectDao.listSubjectByLabelIds(ids);

        // 转换为前端需要的VO对象
        List<InterviewQuestionVO.Interview> views = subjectInfos.stream()
                                                                .map(item -> {
                                                                    InterviewQuestionVO.Interview view = new InterviewQuestionVO.Interview();
                                                                    view.setSubjectName(item.getSubjectName()); // 面试题题干
                                                                    view.setSubjectAnswer(item.getSubjectAnswer()); // 面试题参考答案
                                                                    view.setLabelName(item.getLabelName()); // 标签名称
                                                                    // 拼接关键词：分类名称-标签名称
                                                                    view.setKeyWord(String.format("%s-%s", item.getCategoryName(), item.getLabelName()));
                                                                    return view;
                                                                })
                                                                .collect(Collectors.toList());

        // 面试题数量超过8道时，随机打乱并截取前8道
        if (views.size() > 8) {
            Collections.shuffle(views); // 随机打乱列表
            views = views.subList(0, 8); // 截取前8道
        }

        // 封装返回结果
        InterviewQuestionVO vo = new InterviewQuestionVO();
        vo.setQuestionList(views);
        return vo;
    }

    /**
     * 面试结果提交：计算用户作答的平均分，生成评分提示语
     * 核心逻辑：累加所有题目分数 -> 计算平均分 -> 调用工具类生成评分提示
     *在这段 JiChiInterviewEngine 代码中，submit 方法的评分逻辑完全依赖前端传过来的分数，
     * 后端仅仅做了统计（求和、求平均）和文案包装的工作，没有进行任何实质性的“批改”或“重新打分”。
     * @param req 包含用户每题得分的请求参数
     * @return InterviewResultVO 包含平均分、每题评分提示、平均分提示
     */
    @Override
    public InterviewResultVO submit(InterviewSubmitReq req) {
        // 获取用户作答的所有题目得分列表
        List<InterviewSubmitReq.Submit> submits = req.getQuestionList();

        // 计算总分和平均分
        double total = submits.stream().mapToDouble(InterviewSubmitReq.Submit::getUserScore).sum();
        double avg = total / submits.size();

        // 生成平均分对应的提示语（如"平均分4.5，优秀"）
        String avtTips = EvaluateUtils.avgEvaluate(avg);

        // 生成每题的评分提示语（去重后拼接，如"Java基础：得分5，优秀；SpringBoot：得分3，良好"）
        String tips = submits.stream()
                             .map(item -> {
                                 String keyWord = item.getLabelName(); // 题目标签
                                 String evaluate = EvaluateUtils.evaluate(item.getUserScore()); // 单题评分提示
                                 return String.format(evaluate, keyWord);
                             })
                             .distinct() // 去重，避免同一标签重复提示
                             .collect(Collectors.joining(";"));

        // 封装返回结果
        InterviewResultVO vo = new InterviewResultVO();
        vo.setAvgScore(avg); // 平均分
        vo.setTips(tips); // 每题评分提示
        vo.setAvgTips(avtTips); // 平均分提示
        return vo;
    }

}