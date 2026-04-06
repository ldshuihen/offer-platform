package com.xi.wx.controller;

import com.xi.wx.handler.WxChatMsgFactory;
import com.xi.wx.handler.WxChatMsgHandler;
import com.xi.wx.utils.MessageUtil;
import com.xi.wx.utils.SHA1;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Objects;

/**
 * 测试号管理网址：https://mp.weixin.qq.com/debug/cgi-bin/sandboxinfo?action=showinfo&t=sandbox/index
 * 接口配置信息修改
 * 请填写接口配置信息，此信息需要你有自己的服务器资源，填写的URL需要正确响应微信发送的Token验证，请阅读消息接口使用指南。
 * URL: https://xiew.iepose.cn/callback  // 这里网址后面要加上callback,是因为@PostMapping中有对应的value参数是"callback"
 * Token: 1a0f39d0486c387a
 * 微信测试号中填写的url路径是映射127.0.0.1:3012后再加上callback后的访问路径
 */
@RestController
@Slf4j
public class CallBackController {

    // 微信公众号后台配置的 令牌（Token），必须与你在 [微信公众平台 → 开发管理 → 基本配置] 中填写的 Token 一致。用于签名验证。
    private static final String token = "1a0f39d0486c387a";

    @Resource
    private WxChatMsgFactory wxChatMsgFactory;

    @RequestMapping("/test")
    public String test() {
        return "hello world";
    }

    /**
     * 回调消息校验,GET 请求：验签（验证服务器）
     * @GetMapping("callback") —— 服务器有效性验证（验签）
     * 📌 触发时机：
     * 仅在微信公众平台后台首次配置或修改“服务器地址”时触发一次（手动点击“提交”按钮）。
     * 🎯 核心作用：
     * 证明你拥有这个服务器的控制权，防止他人伪造你的公众号回调地址。
     */
    @GetMapping("callback")
    public String callback(@RequestParam("signature") String signature,
                           @RequestParam("timestamp") String timestamp,
                           @RequestParam("nonce") String nonce,
                           @RequestParam("echostr") String echostr) {
        log.info("get验签请求参数：signature:{}，timestamp:{}，nonce:{}，echostr:{}",
                signature, timestamp, nonce, echostr);
        String shaStr = SHA1.getSHA1(token, timestamp, nonce, "");
        if (signature.equals(shaStr)) {
            return echostr;
        }
        return "unknown";
    }

    /**
     * POST 请求：接收消息与事件,CallBackController 解析消息
     * @PostMapping("callback") —— 接收用户消息与事件推送
     * @PostMapping("callback")和@PostMapping(value="callback")相同，因为其注解的默认属性就是value
     * 这个 callback 是相对路径，会拼接在控制器类的请求路径之后（如果类上有 @RequestMapping）；
     * 如果你的控制器类上没有额外的 @RequestMapping 前缀，那么这个方法的完整访问路径就是 /callback；
     * 如果类上有 @RequestMapping("/wx")，那么完整路径就是 /wx/callback。
     * 📌 触发时机：
     * 每当有用户与你的公众号互动时自动触发，例如：
     * 用户发送文字、图片、语音
     * 用户关注/取消关注
     * 用户点击自定义菜单
     * 用户扫码带参二维码
     * 等等
     * 🎯 核心作用：
     * 接收并处理来自用户的实时消息或系统事件，并可选择性地回复用户。
     */
    @PostMapping(value = "callback", produces = "application/xml;charset=UTF-8")
    public String callback(
            @RequestBody String requestBody,
            @RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam(value = "msg_signature", required = false) String msgSignature) {
        log.info("接收到微信消息：requestBody：{}", requestBody);
        // 解析用户发到服务器的微信消息
        Map<String, String> messageMap = MessageUtil.parseXml(requestBody);
        String msgType = messageMap.get("MsgType");
        String event = messageMap.get("Event") == null ? "" : messageMap.get("Event");
        log.info("msgType:{},event:{}", msgType, event);

        StringBuilder sb = new StringBuilder();
        sb.append(msgType);
        if (!StringUtils.isEmpty(event)) {
            sb.append(".");
            sb.append(event);
        }
        String msgTypeKey = sb.toString();
        // 根据msgTypeKey 判断当前处理器类型
        WxChatMsgHandler wxChatMsgHandler = wxChatMsgFactory.getHandlerByMsgType(msgTypeKey);
        if (Objects.isNull(wxChatMsgHandler)) {
            return "unknown";
        }
        // 这里执行真正的执行程序，返回结果
        String replyContent = wxChatMsgHandler.dealMsg(messageMap);
        log.info("replyContent:{}", replyContent);
        return replyContent;
    }


}
