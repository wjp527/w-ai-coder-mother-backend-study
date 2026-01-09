package com.wjp.waicodermotherbackend.service;

import com.mybatisflex.core.service.IService;
import com.wjp.waicodermotherbackend.model.entity.ChatHistoryOriginal;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.List;

/**
 * 对话历史（加载对话记忆，包括工具调用信息） 服务层。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
public interface ChatHistoryOriginalService extends IService<ChatHistoryOriginal> {

    /**
     * 加载对话历史
     * @param appId 应用ID
     * @param message 消息
     * @param messageType 消息类型
     * @param userId 用户ID
     * @return 添加结果
     */
    boolean addOriginalChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 批量添加对话历史
     * @param chatHistoryOriginals 对话历史
     * @return 添加结果
     */
    boolean addOriginalChatMessageBatch(List<ChatHistoryOriginal> chatHistoryOriginals);

    /**
     * 根据 appId 关联删除对话历史记录
     * @param appId appId
     * @return 删除结果
     */
    boolean deleteByAppId(Long appId);

    /**
     * 将 APP 的对话历史加载到 缓存中（Redis）
     * @param appId appId
     * @param chatMemory 缓存
     * @param maxCount 最大数量
     * @return 加载结果
     */
    int loadOriginalChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);


}
