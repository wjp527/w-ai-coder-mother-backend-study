package com.wjp.waicodermotherbackend.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wjp.waicodermotherbackend.ai.model.message.*;
import com.wjp.waicodermotherbackend.ai.tools.BaseTool;
import com.wjp.waicodermotherbackend.ai.tools.ToolManager;
import com.wjp.waicodermotherbackend.model.entity.ChatHistoryOriginal;
import com.wjp.waicodermotherbackend.model.entity.User;
import com.wjp.waicodermotherbackend.model.enums.ChatHistoryMessageTypeEnum;
import com.wjp.waicodermotherbackend.service.ChatHistoryOriginalService;
import com.wjp.waicodermotherbackend.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param chatHistoryOriginalService 聊天原始服务[包含工具调用信息]
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               ChatHistoryOriginalService chatHistoryOriginalService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 收集用于恢复对话记忆的数据
        StringBuilder aiResponseStringBuilder = new StringBuilder();
        // 每个 Flux 流 可能包含多条工具调用 和 AI_RESPONSE 响应信息，统一收集后批量入库
        List<ChatHistoryOriginal> originalChatHistoryList = new ArrayList<>();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    // todo: 什么时候执行，map 在 doOnComplete 后执行？？？
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, aiResponseStringBuilder,originalChatHistoryList, seenToolIds);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串
                .doOnComplete(() -> {
                    // 工具调用信息入库
                    if(!originalChatHistoryList.isEmpty()) {
                        // 完善 ChatHistoryOriginal 信息
                        originalChatHistoryList.forEach(chatHistory -> {
                            chatHistory.setAppId(appId);
                            chatHistory.setUserId(loginUser.getId());
                        });
                        // 批量入库
                        chatHistoryOriginalService.addOriginalChatMessageBatch(originalChatHistoryList);
                    }

                    // AI response 入库（两种情况：1、没有进行工具调用；2、工具调用结束后，AI 一般还会有一句返回）

                    // 流式响应完成后，添加 AI 消息到对话历史
                    String chatHistoryStr = chatHistoryStringBuilder.toString();
                    // 保存到旧的 chat_history 表（向后兼容）
                    chatHistoryService.addChatMessage(appId, chatHistoryStr, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());

                    // 保存到 chat_history_original 表（完整对话历史）
                    // 注意：aiResponseStringBuilder 在工具调用后会被清空，所以这里只保存工具调用后的 AI 响应（如果有）
                    // 工具调用前的 AI 响应已经保存在 tool_request 的 text 字段中了
                    // 只会保留 工具调用后的 AI 响应
                    String aiResponseStr = aiResponseStringBuilder.toString();
                    if(StrUtil.isNotEmpty(aiResponseStr)) {
                        // 工具调用后的 AI 响应，需要单独保存
                        chatHistoryOriginalService.addOriginalChatMessage(appId, aiResponseStr, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }

                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    chatHistoryOriginalService.addOriginalChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     * @param chunk                     JSON 消息块
     * @param chatHistoryStringBuilder  用于收集对话历史数据
     * @param aiResponseStringBuilder   用于收集 AI 响应数据 [只会保留工具调用后的AI响应]
     * @param originalChatHistoryList   用于收集原始对话数据
     * @param seenToolIds               用于记录已经见过的工具ID
     */
    private String handleJsonMessageChunk(String chunk,
                                          StringBuilder chatHistoryStringBuilder,
                                          StringBuilder aiResponseStringBuilder,
                                          List<ChatHistoryOriginal> originalChatHistoryList,
                                          Set<String> seenToolIds) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            // AI 响应
            case AI_RESPONSE -> {
                AIResponseMessage aiMessage = JSONUtil.toBean(chunk, AIResponseMessage.class);
                String data = aiMessage.getData();
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                // 对于 AI 响应内容，与展示数据处理逻辑相同
                aiResponseStringBuilder.append(data);
                return data;
            }
            // 工具请求
            case TOOL_REQUEST -> {
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                String toolId = toolRequestMessage.getId();
                String toolName = toolRequestMessage.getName();
                // 检查是否是第一次看到这个工具 ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    // 第一次调用这个工具，记录 ID 并完整返回工具信息
                    seenToolIds.add(toolId);
                    // 根据工具名称获取工具实例
                    BaseTool tool = toolManager.getToolByName(toolName);
                    // 返回格式化的工具调用信息
                    String result = tool.generateToolRequestResponse();
                    return result;
                } else {
                    // 不是第一次调用这个工具，直接返回空
                    return "";
                }
            }
            // 工具执行结果
            case TOOL_EXECUTED -> {
                // 处理工具调用信息
                processToolExecutionMessage(aiResponseStringBuilder, chunk, originalChatHistoryList);
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                String toolName = toolExecutedMessage.getName();
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());

                BaseTool tool = toolManager.getToolByName(toolName);
                String result = tool.generateToolExecutedResult(jsonObject);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    /**
     * 解析处理工具调用相关信息
     * @param aiResponseStringBuilder
     * @param chunk
     * @param originalChatHistoryList
     */
    private void processToolExecutionMessage(StringBuilder aiResponseStringBuilder, String chunk, List<ChatHistoryOriginal> originalChatHistoryList) {
        // 解析 chunk
        ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        // 构造工具调用请求对象(工具调用结果的数据就是从请求中拿到的，所以直接在这里处理调用请求信息)
        String aiResponseStr = aiResponseStringBuilder.toString();
        ToolRequestMessage toolRequestMessage = new ToolRequestMessage();
        // 从 toolExecutedMessage 中获取工具请求的信息（id, name, arguments）
        toolRequestMessage.setId(toolExecutedMessage.getId());
        toolRequestMessage.setName(toolExecutedMessage.getName());
        toolRequestMessage.setArguments(toolExecutedMessage.getArguments());
        toolRequestMessage.setText(aiResponseStr);

        // 解析为 JSON
        String toolRequestJsonStr = JSONUtil.toJsonStr(toolRequestMessage);

        // 构造 ChatHistory 存入列表
        // 工具调用请求
        ChatHistoryOriginal toolRequestHistory = ChatHistoryOriginal.builder()
                .message(toolRequestJsonStr)
                .messageType(ChatHistoryMessageTypeEnum.TOOL_EXECUTION_REQUEST.getValue())
                .build();
        originalChatHistoryList.add(toolRequestHistory);
        
        // 工具调用结果
        ChatHistoryOriginal toolResultHistory = ChatHistoryOriginal.builder()
                .message(chunk)
                .messageType(ChatHistoryMessageTypeEnum.TOOL_EXECUTION_RESULT.getValue())
                .build();
        originalChatHistoryList.add(toolResultHistory);

        // AI 响应内容暂时结束，置空 aiResponseStringBuilder（因为工具调用后，AI 可能会继续响应，需要重新收集）
        aiResponseStringBuilder.setLength(0);


    }

}
