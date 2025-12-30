package com.wjp.waicodermotherbackend.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wjp.waicodermotherbackend.model.dto.chathistory.ChatHistoryQueryRequest;
import com.wjp.waicodermotherbackend.model.entity.ChatHistory;
import com.wjp.waicodermotherbackend.model.entity.User;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话消息
     * @param appId 应用Id
     * @param message 消息
     * @param messageType 消息类型
     * @param userId 用户Id
     * @return 是否添加成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用Id删除对话消息
     * @param appId 应用Id
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 获取应用对话历史列表
     * @param appId 应用Id
     * @param pageSize 页面大小
     * @param lastCreateTime 最后创建时间
     * @param loginUser 登录用户
     * @return 应用对话历史列表
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 获取查询包装类 【游标】
     *
     * @param chatHistoryQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
