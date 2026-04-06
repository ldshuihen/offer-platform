package com.xi.wx.handler;

/**
 * ✅ 作用：
 * 定义系统支持的 微信消息/事件类型常量，作为后续分发处理器的“键”。
 */
public enum WxChatMsgTypeEnum {

    SUBSCRIBE("event.subscribe", "用户关注事件"),
    TEXT_MSG("text", "接收用户文本消息");

    private String msgType; // 微信原始消息类型标识（如 "text"）
    private String desc;    // 中文描述

    WxChatMsgTypeEnum(String msgType, String desc) {
        this.msgType = msgType;
        this.desc = desc;
    }

    // 构造函数 + 静态查找方法
    public static WxChatMsgTypeEnum getByMsgType(String msgType) {
        for (WxChatMsgTypeEnum wxChatMsgTypeEnum : WxChatMsgTypeEnum.values()) {
            if (wxChatMsgTypeEnum.msgType.equals(msgType)) {
                return wxChatMsgTypeEnum;
            }
        }
        return null;
    }

}
