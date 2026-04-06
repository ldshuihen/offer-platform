package com.xi.interview.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.xi.interview.api.enums.EngineEnum;
import com.xi.interview.api.req.InterviewSubmitReq;
import com.xi.interview.api.req.StartReq;
import com.xi.interview.api.vo.InterviewQuestionVO;
import com.xi.interview.api.vo.InterviewResultVO;
import com.xi.interview.api.vo.InterviewVO;
import com.xi.interview.server.service.InterviewEngine;
import com.xi.interview.server.util.EvaluateUtils;
import com.xi.interview.server.util.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 这套 Java 代码实现了一个基于阿里云通义千问大模型（qwen1.5-110b-chat）的面试题生成与面试评分系统，核心功能分为三部分：
 * 面试题生成：根据输入的关键词，调用大模型生成对应的面试题及分类标签；
 * 面试作答评分：根据用户提交的面试题答案，调用大模型对答案打分（0-5 分）并生成参考答案；
 * 结果汇总：对所有题目的评分计算平均分，并生成对应的评价提示语。
 * 同时，代码中封装了通用的 HTTP 工具类，用于和大模型的 API 进行网络交互。
 *
 * 2. AlBLInterviewEngine.java：面试引擎核心逻辑
 * 实现InterviewEngine接口，对接阿里云通义千问大模型，完成面试题生成和评分：
 * 核心枚举：EngineEnum.ALI_BL标识该引擎为阿里云通义千问；
 * 核心方法：
 * 表格
 * 方法	功能
 * analyse	对输入关键词做基础封装（暂无大模型交互，仅初始化面试题结构）
 * start	随机选取 8 个关键词，调用大模型生成面试题 + 标签
 * submit	批量调用大模型对用户答案评分，计算平均分 + 生成评价提示语
 * buildInterview	单个关键词调用大模型生成面试题的核心逻辑
 * buildInterviewScore	单个题目 + 答案调用大模型评分的核心逻辑
 *
 * 四、大模型交互的关键细节
 * 鉴权方式：通过Authorization请求头携带Bearer + apiKey（阿里云通义千问的 API 密钥）；
 * 模型版本：指定qwen1.5-110b-chat（通义千问 1.5 版本，1100 亿参数的对话模型）；
 * 提示词设计：
 * 生成面试题：要求大模型根据关键词生成 1 道题 + 标签，并按{"labelName":"分类名称","subjectName":"题目"}返回 JSON；
 * 评分：要求大模型根据题目 + 答案打 0-5 分，并返回{"userScore":"分数","subjectAnswer":"参考答案"}；
 * 响应解析：大模型返回的文本包含 Markdown 格式的 JSON（```json 包裹），需提取纯 JSON 片段解析；
 * 性能优化：异步批量调用 + 连接池复用，减少网络开销和等待时间。
 * 五、整体流程总结
 * 前端 / 上游系统传入「面试关键词列表」；
 * start方法随机选 8 个关键词，异步调用大模型生成面试题；
 * 用户作答后，submit方法异步调用大模型对每道题评分；
 * 汇总所有评分，计算平均分并生成评价提示语；
 * 全程通过HttpUtils封装的 HTTP 请求与大模型 API 交互，保证网络通信的稳定性。
 */
@Service
@Slf4j
@SuppressWarnings("all")
public class AlBLInterviewEngine implements InterviewEngine {

    //大模型key 换自己的token
    private static final String apiKey = "sk-c111503bbf824bc8befec7cd3145859e";

    private static final String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    //private static final String DEFAULT_MODEL = "qwen1.5-110b-chat";
    private static final String DEFAULT_MODEL = "qwen-plus";

    @Override
    public EngineEnum engineType() {
        return EngineEnum.ALI_BL;
    }

    @Override
    public InterviewVO analyse(List<String> KeyWords) {
        InterviewVO vo = new InterviewVO();
        List<InterviewVO.Interview> questionList = KeyWords.stream().map(item -> {
            InterviewVO.Interview interview = new InterviewVO.Interview();
            interview.setKeyWord(item);
            interview.setCategoryId(-1L);
            interview.setLabelId(-1L);
            return interview;
        }).collect(Collectors.toList());
        vo.setQuestionList(questionList);
        return vo;
    }

    // 批量调用大模型对用户答案评分，计算平均分 + 生成评价提示语
    @Override
    public InterviewResultVO submit(InterviewSubmitReq req) {
        System.out.println("调用大模型评分");

        long start = System.currentTimeMillis();
        // 异步批量处理（性能优化）
        //为提升效率，代码使用CompletableFuture.supplyAsync异步批量调用大模型：
        List<CompletableFuture<InterviewSubmitReq.Submit>> list = req.getQuestionList().stream().
                map(keyword -> CompletableFuture.supplyAsync(() -> buildInterviewScore(keyword)))
                .collect(Collectors.toList());
        List<InterviewSubmitReq.Submit> interviews = new ArrayList<>();
        list.forEach(future -> {
            try {
                if (Objects.nonNull(future)) {
                    InterviewSubmitReq.Submit interview = future.get();
                    interviews.add(interview);
                }
            } catch (Exception e) {
                log.error("buildInterview.get.error", e);
            }
        });
        req.setQuestionList(interviews);
        String tips = interviews.stream().map(item -> {
            String keyWord = item.getLabelName();
            String evaluate = EvaluateUtils.evaluate(item.getUserScore());
            return String.format(evaluate, keyWord);
        }).distinct().collect(Collectors.joining(";"));
        List<InterviewSubmitReq.Submit> submits = req.getQuestionList();
        double total = submits.stream().mapToDouble(InterviewSubmitReq.Submit::getUserScore).sum();
        double avg = total / submits.size();
        String avtTips = EvaluateUtils.avgEvaluate(avg);
        InterviewResultVO vo = new InterviewResultVO();
        vo.setAvgScore(avg);
        vo.setTips(tips);
        vo.setAvgTips(avtTips);
        log.info("submit total cost {}", System.currentTimeMillis() - start);
        return vo;

    }

    @Override
    public InterviewQuestionVO start(StartReq req) {

        long start = System.currentTimeMillis();
        List<String> keywords = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            StartReq.Key key = req.getQuestionList().get(new Random().nextInt(req.getQuestionList().size()));
            keywords.add(key.getKeyWord());
        }

        List<CompletableFuture<InterviewQuestionVO.Interview>> list = keywords.stream().
                map(keyword -> CompletableFuture.supplyAsync(() -> buildInterview(keyword)))
                .collect(Collectors.toList());
        List<InterviewQuestionVO.Interview> interviews = new ArrayList<>();
        list.forEach(future -> {
            try {
                if (Objects.nonNull(future)) {
                    InterviewQuestionVO.Interview interview = future.get();
                    interviews.add(interview);
                }
            } catch (Exception e) {
                log.error("buildInterview.get.error", e);
            }
        });
        InterviewQuestionVO vo = new InterviewQuestionVO();
        vo.setQuestionList(interviews);
        log.info("start total cost {}", System.currentTimeMillis() - start);
        return vo;

    }


     // buildInterviewScore	单个题目 + 答案调用大模型评分的核心逻辑
    private static InterviewSubmitReq.Submit buildInterviewScore(InterviewSubmitReq.Submit submit) {
        //步骤 1：构造大模型请求参数
        //调用大模型的buildInterview/buildInterviewScore方法时，构造符合阿里云通义千问 API 的请求参数：
        long start = System.currentTimeMillis();
        Map<String, Object> reqMap = new HashMap<>();
        // 1. 构造请求JSON体
        JSONObject jsonData = new JSONObject();
        jsonData.put("model", DEFAULT_MODEL); // 指定大模型版本
        JSONObject input = new JSONObject();
        JSONObject message2 = new JSONObject();
        message2.put("role", "user");// 角色为用户
        // 提示词：要求大模型生成面试题/评分，并按指定JSON结构返回
        String keyword = String.format("题目:%s,答案:%s", submit.getSubjectName(), submit.getUserAnswer());
        String subject = String.format("根据题目和答案 %s ;评一个分数0-5分及参考答案并按照数据结{\"userScore\":\"用户分数\",\"subjectAnswer\":\"参考答案\"}构返json数据，字数要求在200字以内", keyword);
        log.info("buildInterview {}", subject);
        message2.put("content", subject);// 提示词内容
        input.put("messages", new JSONObject[]{message2});
        jsonData.put("input", input);
        jsonData.put("parameters", new JSONObject());

        // 2. 构造请求头（鉴权+格式）
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Authorization", "Bearer " + apiKey);// 大模型API密钥
        headerMap.put("Content-Type", "application/json");
        headerMap.put("X-DashScope-SSE", "enable");// 启用SSE流式响应

        //步骤 2：调用大模型 API
        //通过HttpUtils.executePost发送 POST 请求到阿里云通义千问的 API 地址：
        //String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        String body = HttpUtils.executePost(url, jsonData.toJSONString(), headerMap);

        //步骤 3：解析大模型响应
        //大模型返回的是 SSE 流式响应，代码中提取最后一段data:格式的内容，解析出 JSON 结果：
        // 输出响应结果
        // 提取响应中最后一段data:内容
        int index = body.lastIndexOf("data:");
        String substring = body.substring(index + 5, body.length() - 1);
        JSONObject jsonObject = JSONObject.parseObject(substring);
        // 提取大模型生成的文本内容
        String text = jsonObject.getJSONObject("output").getString("text");
        // 提取文本中的JSON片段（大模型返回带```json包裹的内容）
        int jsonIndex = text.lastIndexOf("```json");
        //String json = text.substring(jsonIndex + 7, text.lastIndexOf("```"));
        String json = text;

        InterviewSubmitReq.Submit interviews = JSONObject.parseObject(json, InterviewSubmitReq.Submit.class);
        interviews.setLabelName(submit.getLabelName());
        interviews.setSubjectName(submit.getSubjectName());
        interviews.setUserAnswer(submit.getUserAnswer());
        log.info("cost {} data:{}", System.currentTimeMillis() - start, JSON.toJSONString(interviews));
        return interviews;
    }

    // buildInterview	根据关键字生成面试题和标签的核心逻辑
    private static InterviewQuestionVO.Interview buildInterview(String keyword) {
        long start = System.currentTimeMillis();
        JSONObject jsonData = new JSONObject();
        jsonData.put("model", DEFAULT_MODEL);
        JSONObject input = new JSONObject();
        JSONObject message2 = new JSONObject();
        message2.put("role", "user");
        String subject = String.format("根据以下关键字生成1道面试题和标签 %s 并按照数据结{\"labelName\":\"分类名称\",\"subjectName\":\"题目\"}构返json数据", keyword);
        log.info("buildInterview {}", subject);
        message2.put("content", subject);
        input.put("messages", new JSONObject[]{message2});
        jsonData.put("input", input);
        jsonData.put("parameters", new JSONObject());


        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Authorization", "Bearer " + apiKey);
        headerMap.put("Content-Type", "application/json");
        headerMap.put("X-DashScope-SSE", "enable");

        //String url = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        String body = HttpUtils.executePost(url, jsonData.toJSONString(), headerMap);

        // 输出响应结果
        int index = body.lastIndexOf("data:");
        String substring = body.substring(index + 5, body.length() - 1);
        JSONObject jsonObject = JSONObject.parseObject(substring);
        String text = jsonObject.getJSONObject("output").getString("text");
        int jsonIndex = text.lastIndexOf("```json");
        //String json = text.substring(jsonIndex + 7, text.lastIndexOf("```"));
        String json = text;
        // 解析为业务对象
        InterviewQuestionVO.Interview interviews = JSONObject.parseObject(json, InterviewQuestionVO.Interview.class);
        log.info("cost {} data:{}", System.currentTimeMillis() - start, JSON.toJSONString(interviews));
        return interviews;
    }

}
