package com.xi.subject.application.mq;

import com.alibaba.fastjson.JSON;
import com.xi.subject.domain.entity.SubjectLikedBO;
import com.xi.subject.domain.service.SubjectLikedDomainService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 *  RocketMQ 消息消费者，主要用于处理“题目点赞”相关的业务。它监听特定的主题（subject-liked），一旦接收到消息，就会解析消息内容并调用业务服务进行处理。
 * 核心功能
 * 这是一个 Spring Boot 集成 RocketMQ 的消费者组件，负责异步处理“题目点赞”或“取消点赞”的消息。
 * 监听机制：它监听名为 subject-liked 的主题。
 * 消息处理：接收到消息后，将 JSON 格式的消息体反序列化为 SubjectLikedBO 对象。
 * 业务调用：将解析出的对象传递给 SubjectLikedDomainService，执行具体的业务逻辑（如更新数据库中的点赞数、记录用户行为等）。
 * 关键注解解析
 * @Component：将该类交给 Spring 容器管理，使其成为一个 Bean。
 * @RocketMQMessageListener：
 *     topic = "subject-liked"：指定监听的主题名称。
 *     consumerGroup = "subject-liked-consumer"：指定消费者组名，用于标识一组消费者共同消费消息。
 * @Slf4j：Lombok 提供的注解，自动生成日志记录器 log。
 * 代码逻辑流程
 * 接收消息：onMessage(String s) 方法被触发，参数 s 是接收到的原始 JSON 字符串。
 * 打印日志：使用 System.out.println 打印接收到的消息内容（在生产环境中，建议使用 log.info 替代）。
 * 反序列化：使用 JSON.parseObject 将 JSON 字符串转换为 SubjectLikedBO 业务对象。
 * 执行业务：调用 subjectLikedDomainService.syncLikedByMsg(subjectLikedBO) 方法，同步处理点赞消息。
 * 潜在改进建议
 * 异常处理：当前代码没有捕获反序列化或业务处理可能出现的异常。如果消息格式错误或服务调用失败，可能会导致消息消费阻塞。建议添加 try-catch 块进行容错处理。
 * 日志记录：建议使用 log 对象替代 System.out.println，以便更好地控制日志级别和输出格式。
 * 幂等性：在实际业务中，消息队列可能会出现消息重复投递的情况，因此 syncLikedByMsg 方法需要保证幂等性（即同一条消息被消费多次不会产生副作用）。
 * 总结
 * 这段代码是领域驱动设计（DDD）中应用层与消息中间件的桥梁，它实现了业务解耦和异步处理，确保点赞操作不会阻塞主业务流程，提高了系统的响应速度和可靠性。
 */
@Component
@RocketMQMessageListener(topic = "subject-liked", consumerGroup = "subject-liked-consumer")
@Slf4j
public class SubjectLikedConsumer implements RocketMQListener<String> {

    @Resource
    private SubjectLikedDomainService subjectLikedDomainService;

    @Override
    public void onMessage(String s) {
        System.out.println("接受点赞mq,消息为" + s);
        SubjectLikedBO subjectLikedBO = JSON.parseObject(s, SubjectLikedBO.class);
        subjectLikedDomainService.syncLikedByMsg(subjectLikedBO);
    }

}
