package com.xi.wx.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * SubscribeMsgHandler.java —— 关注事件处理器（策略实现1）
 * ✅ 作用：
 * 专门处理 用户关注公众号 的事件（MsgType=event, Event=subscribe）。
 * 📌 特点：
 * 使用 @Component 注册为 Spring Bean，自动被工厂发现。
 * 返回 标准微信文本回复 XML。
 * ✅ 总结：这是策略模式中的 一个具体策略（Concrete Strategy）。
 */
@Component
@Slf4j
public class SubscribeMsgHandler implements WxChatMsgHandler {

    @Override
    public WxChatMsgTypeEnum getMsgType() {
        return WxChatMsgTypeEnum.SUBSCRIBE;
    }

    @Override
    public String dealMsg(Map<String, String> messageMap) {
        log.info("触发用户关注事件！");
        String fromUserName = messageMap.get("FromUserName");
        String toUserName = messageMap.get("ToUserName");

        // 构建欢迎语
        String subscribeContent = "感谢您的关注，我是xew ！欢迎来学习从0到1社区项目";

        // 拼接微信要求的 XML 回复格式
        String content = "<xml>\n" +
                "  <ToUserName><![CDATA[" + fromUserName + "]]></ToUserName>\n" +
                "  <FromUserName><![CDATA[" + toUserName + "]]></FromUserName>\n" +
                "  <CreateTime>12345678</CreateTime>\n" +
                "  <MsgType><![CDATA[text]]></MsgType>\n" +
                "  <Content><![CDATA[" + subscribeContent + "]]></Content>\n" +
                "</xml>";
        return content;
    }

}
