package com.xi.interview.server.service.impl;

import com.google.common.base.Preconditions;
import com.xi.interview.api.req.InterviewReq;
import com.xi.interview.api.req.InterviewSubmitReq;
import com.xi.interview.api.req.StartReq;
import com.xi.interview.api.vo.InterviewQuestionVO;
import com.xi.interview.api.vo.InterviewResultVO;
import com.xi.interview.api.vo.InterviewVO;
import com.xi.interview.server.dao.SubjectDao;
import com.xi.interview.server.entity.po.SubjectLabel;
import com.xi.interview.server.service.InterviewEngine;
import com.xi.interview.server.service.InterviewService;
import com.xi.interview.server.util.PDFUtil;
import com.xi.interview.server.util.keyword.KeyWordUtil;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试服务核心实现类（门面模式）
 * 核心职责：
 * 1. 作为前端交互的统一入口，屏蔽底层不同面试引擎（自研/阿里云通义千问）的实现差异；
 * 2. 实现PDF解析、关键词提取、引擎路由、结果汇总的核心流程；
 * 3. 通过ApplicationContextAware动态加载所有InterviewEngine实现类，实现引擎的可插拔扩展。
 *
 * 设计模式：
 * - 门面模式（Facade）：对外提供统一的analyse/start/submit接口，内部路由到不同引擎；
 * - 策略模式：不同引擎（JiChi/ALI_BL）作为策略，通过engineMap动态选择；
 * - 初始化模式：通过ApplicationContextAware在Bean加载时初始化引擎映射，避免硬编码。
 */
@Service
public class InterviewServiceImpl implements InterviewService, ApplicationContextAware {

    /**
     * 面试引擎映射表（引擎枚举名称 -> 引擎实现类）
     * 作用：缓存所有实现InterviewEngine接口的Bean，便于根据前端传入的引擎类型快速路由
     */
    private static final Map<String, InterviewEngine> engineMap = new HashMap<>();

    /**
     * 题库标签数据访问层，用于加载所有标签词库（供关键词提取使用）
     */
    @Resource
    private SubjectDao subjectLabelDao;

    /**
     * Spring上下文感知方法（ApplicationContextAware接口实现）
     * 作用：在Bean初始化时，自动扫描并加载所有InterviewEngine实现类到engineMap
     * @param applicationContext Spring应用上下文
     * @throws BeansException 上下文获取异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 获取所有实现InterviewEngine接口的Bean实例
        Collection<InterviewEngine> engines = applicationContext.getBeansOfType(InterviewEngine.class).values();
        // 遍历引擎实例，按引擎枚举名称（如JI_CHI/ALI_BL）存入映射表
        for (InterviewEngine engine : engines) {
            engineMap.put(engine.engineType().name(), engine);
        }
    }

    /**
     * 面试题分析入口方法
     * 核心流程：PDF解析 -> 关键词提取 -> 引擎路由 -> 返回分析结果
     * @param req 面试分析请求（包含PDF URL、指定引擎类型）
     * @return InterviewVO 包含匹配的标签、分类、关键词等信息
     */
    @Override
    public InterviewVO analyse(InterviewReq req) {
        // 步骤1：解析PDF并提取关键词列表（核心依赖PDFUtil和KeyWordUtil）
        List<String> keyWords = buildKeyWords(req.getUrl());
        // 步骤2：根据请求指定的引擎类型，从映射表中获取对应的引擎实例
        InterviewEngine engine = engineMap.get(req.getEngine());
        // 步骤3：参数校验（使用Guava的Preconditions，引擎不能为空）
        Preconditions.checkArgument(!Objects.isNull(engine), "引擎不能为空！");
        // 步骤4：路由到具体引擎的analyse方法，返回结果
        return engine.analyse(keyWords);
    }

    /**
     * 生成面试题入口方法
     * 核心流程：引擎路由 -> 调用对应引擎的start方法生成面试题
     * @param req 面试启动请求（包含引擎类型、标签ID列表等）
     * @return InterviewQuestionVO 包含8道面试题（题干、答案、标签等）
     */
    @Override
    public InterviewQuestionVO start(StartReq req) {
        // 步骤1：根据请求指定的引擎类型获取引擎实例
        InterviewEngine engine = engineMap.get(req.getEngine());
        // 步骤2：参数校验，引擎不能为空
        Preconditions.checkArgument(!Objects.isNull(engine), "引擎不能为空！");
        // 步骤3：路由到具体引擎的start方法生成面试题
        return engine.start(req);
    }

    /**
     * 面试结果提交入口方法
     * 核心流程：引擎路由 -> 调用对应引擎的submit方法评分 + 结果汇总
     * @param req 面试提交请求（包含用户作答、引擎类型等）
     * @return InterviewResultVO 包含平均分、评分提示、每题评价等
     */
    @Override
    public InterviewResultVO submit(InterviewSubmitReq req) {
        // 步骤1：根据请求指定的引擎类型获取引擎实例
        InterviewEngine engine = engineMap.get(req.getEngine());
        // 步骤2：参数校验，引擎不能为空
        Preconditions.checkArgument(!Objects.isNull(engine), "引擎不能为空！");
        // 步骤3：路由到具体引擎的submit方法完成评分和结果汇总
        return engine.submit(req);
    }

    /**
     * 构建关键词列表（核心工具方法）
     * 核心流程：PDF文本提取 -> 初始化关键词词库 -> 基于词库提取PDF中的关键词
     * @param url PDF文件的访问地址
     * @return List<String> 从PDF中提取的面试关键词列表（如"Java基础"、"SpringBoot"）
     */
    private List<String> buildKeyWords(String url) {
        // 步骤1：解析PDF文件，提取文本内容（依赖PDFUtil工具类）
        String pdfText = PDFUtil.getPdfText(url);

        // 步骤2：初始化关键词提取工具的词库（仅首次调用时初始化）
        if (!KeyWordUtil.isInit()) {
            // 从数据库加载所有标签名称，作为关键词提取的基准词库
            List<String> list = subjectLabelDao.listAllLabel()
                                               .stream()
                                               .map(SubjectLabel::getLabelName) // 提取标签名称（如"Java"、"MySQL"）
                                               .collect(Collectors.toList());
            // 将标签词库注入关键词提取工具
            KeyWordUtil.addWord(list);
        }

        // 步骤3：基于初始化的词库，从PDF文本中提取匹配的关键词
        return KeyWordUtil.buildKeyWordsLists(pdfText);
    }

}