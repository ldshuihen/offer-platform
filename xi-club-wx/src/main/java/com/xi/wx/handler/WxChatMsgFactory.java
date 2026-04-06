package com.xi.wx.handler;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WxChatMsgFactory.java —— 消息处理器工厂
 * ✅ 作用：
 * 在 Spring 启动时 自动收集所有 WxChatMsgHandler 实现类，并建立 消息类型 → 处理器 的映射关系，供运行时快速查找。
 * 📌 关键机制：
 * InitializingBean：确保在 Spring 容器初始化完成后构建 handlerMap。
 * 自动发现：通过 List<WxChatMsgHandler> 注入所有实现类，无需手动注册。
 * O(1) 查找：运行时通过 msgType 字符串快速定位处理器。
 * ✅ 总结：这是 工厂模式 + 策略模式的枢纽，实现了“配置即生效”的扩展性。
 */
@Component
public class WxChatMsgFactory implements InitializingBean {

    // Spring 自动注入所有 WxChatMsgHandler 的实现 Bean
    @Resource
    private List<WxChatMsgHandler> wxChatMsgHandlerList;

    // 内存映射表：WxChatMsgTypeEnum -> 具体处理器
    private Map<WxChatMsgTypeEnum, WxChatMsgHandler> handlerMap = new HashMap<>();

    // 对外提供查询接口
    public WxChatMsgHandler getHandlerByMsgType(String msgType) {
        WxChatMsgTypeEnum msgTypeEnum = WxChatMsgTypeEnum.getByMsgType(msgType);
        return handlerMap.get(msgTypeEnum);
    }

    // Spring 初始化完成后执行（构建映射表）
    @Override
    public void afterPropertiesSet() throws Exception {
        for (WxChatMsgHandler wxChatMsgHandler : wxChatMsgHandlerList) {
            handlerMap.put(wxChatMsgHandler.getMsgType(), wxChatMsgHandler);
        }
    }

}
