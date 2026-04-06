package com.xi.interview.server.service;

import com.xi.interview.api.req.InterviewReq;
import com.xi.interview.api.req.InterviewSubmitReq;
import com.xi.interview.api.req.StartReq;
import com.xi.interview.api.vo.InterviewQuestionVO;
import com.xi.interview.api.vo.InterviewResultVO;
import com.xi.interview.api.vo.InterviewVO;

/**
 * 面试核心服务接口（门面接口）
 * 定位：作为面试系统对外提供的核心能力入口，定义了「面试分析、面试题生成、面试评分」三大核心流程的标准化接口；
 * 设计原则：
 * 1. 接口层只定义能力契约，不涉及具体实现（具体逻辑由InterviewServiceImpl实现，内部路由到不同InterviewEngine）；
 * 2. 入参均为Req请求对象，出参均为VO视图对象，保证参数封装和返回值标准化；
 * 3. 屏蔽底层引擎差异（如阿里云通义千问/自研引擎），对上游提供统一调用方式。
 */
public interface InterviewService {

    /**
     * 面试关键词分析接口
     * 核心能力：解析PDF简历/岗位描述URL，提取关键词并初始化面试题结构（无实际面试题生成，仅关键词维度分析）；
     * 业务流程：PDF解析 → 关键词提取 → 封装关键词列表返回；
     *
     * @param req 面试分析请求参数（包含PDF文件URL、指定使用的引擎类型）
     * @return InterviewVO 包含提取的关键词列表、初始化的面试题基础结构（无具体题目）
     */
    InterviewVO analyse(InterviewReq req);

    /**
     * 生成面试题接口
     * 核心能力：基于分析阶段的关键词，调用指定引擎（如阿里云通义千问）生成8道面试题（含题干、分类标签）；
     * 业务流程：随机选取8个关键词 → 异步调用大模型生成面试题 → 封装题目列表返回；
     *
     * @param req 面试启动请求参数（包含引擎类型、关键词列表等）
     * @return InterviewQuestionVO 包含8道面试题的完整信息（题干、分类标签等）
     */
    InterviewQuestionVO start(StartReq req);

    /**
     * 面试作答提交与评分接口
     * 核心能力：接收用户作答内容，调用指定引擎对每道题评分（0-5分）、生成参考答案，最终汇总平均分和评价提示；
     * 业务流程：批量异步调用大模型评分 → 计算平均分 → 生成个性化评价提示 → 封装结果返回；
     *
     * @param req 面试提交请求参数（包含用户作答列表、引擎类型等）
     * @return InterviewResultVO 包含每题评分、参考答案、平均分、评价提示语等
     */
    InterviewResultVO submit(InterviewSubmitReq req);
}