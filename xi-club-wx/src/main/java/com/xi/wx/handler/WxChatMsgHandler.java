package com.xi.wx.handler;

import java.util.Map;

/**
 * ✅ 作用：
 * 定义所有消息处理器必须实现的 统一契约（Contract）。
 */
public interface WxChatMsgHandler {

    WxChatMsgTypeEnum getMsgType();

    String dealMsg(Map<String, String> messageMap);

}
