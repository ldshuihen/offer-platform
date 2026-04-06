package com.xi.wx.handler;

import com.xi.wx.redis.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 🔹 ReceiveTextMsgHandler.java —— 文本消息处理器（策略实现2）
 * ✅ 作用：
 * 处理用户发送的 文本消息，当前逻辑是：当用户发送“验证码”时，生成并返回一个随机验证码。
 */
@Component
@Slf4j
public class ReceiveTextMsgHandler implements WxChatMsgHandler {

    private static final String KEY_WORD = "验证码";

    private static final String LOGIN_PREFIX = "loginCode";

    @Resource
    private RedisUtil redisUtil;

    @Override
    public WxChatMsgTypeEnum getMsgType() {
        return WxChatMsgTypeEnum.TEXT_MSG;
    }

    @Override
    public String dealMsg(Map<String, String> messageMap) {
        log.info("接收到文本消息事件");
        String content = messageMap.get("Content");

        // 只响应特定关键词
        if (!KEY_WORD.equals(content)) {
            return "";
        }
        String fromUserName = messageMap.get("FromUserName");
        String toUserName = messageMap.get("ToUserName");

        Random random = new Random();

        // 生成随机验证码（0~999）
        int num = random.nextInt(1000);

        // 存入 Redis：key = "loginCode", value = "用户OpenID", 过期5分钟
        String numKey = redisUtil.buildKey(LOGIN_PREFIX, String.valueOf(num));
        redisUtil.setNx(numKey, fromUserName, 5L, TimeUnit.MINUTES);
        String numContent = "您当前的验证码是：" + num + "！ 5分钟内有效";

        // 返回验证码消息
        String replyContent = "<xml>\n" +
                "  <ToUserName><![CDATA[" + fromUserName + "]]></ToUserName>\n" +
                "  <FromUserName><![CDATA[" + toUserName + "]]></FromUserName>\n" +
                "  <CreateTime>12345678</CreateTime>\n" +
                "  <MsgType><![CDATA[text]]></MsgType>\n" +
                "  <Content><![CDATA[" + numContent + "]]></Content>\n" +
                "</xml>";

        return replyContent;
    }

}
