package com.wjp.waicodermotherbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjp.waicodermotherbackend.ai.model.message.ToolExecutedMessage;
import com.wjp.waicodermotherbackend.ai.model.message.ToolRequestMessage;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.model.entity.ChatHistoryOriginal;
import com.wjp.waicodermotherbackend.mapper.ChatHistoryOriginalMapper;
import com.wjp.waicodermotherbackend.model.enums.ChatHistoryMessageTypeEnum;
import com.wjp.waicodermotherbackend.service.ChatHistoryOriginalService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话历史（加载对话记忆，包括工具调用信息） 服务层实现。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@Service
@Slf4j
public class ChatHistoryOriginalServiceImpl extends ServiceImpl<ChatHistoryOriginalMapper, ChatHistoryOriginal>  implements ChatHistoryOriginalService{

    /**
     * 加载对话历史
     * @param appId 应用ID
     * @param message 消息
     * @param messageType 消息类型
     * @param userId 用户ID
     * @return 添加结果
     */
    @Override
    public boolean addOriginalChatMessage(Long appId, String message, String messageType, Long userId) {
        // 1、参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用Id不能为空");
        ThrowUtils.throwIf(message == null || message.length() == 0, ErrorCode.PARAMS_ERROR, "消息不能为空");
        ThrowUtils.throwIf(messageType == null || messageType.length() == 0, ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户Id不能为空");

        // 2、验证消息类型是否有效
        ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(messageTypeEnum == null, ErrorCode.PARAMS_ERROR, "消息类型无效");

        // 3、对话消息入库
        ChatHistoryOriginal chatHistoryOriginal = ChatHistoryOriginal.builder()
                .appId(appId)
                .message(message)
                .messageType(messageType)
                .userId(userId)
                .build();

        return this.save(chatHistoryOriginal);
    }

    /**
     * 批量添加对话历史
     * @param chatHistoryOriginals 对话历史
     * @return 添加结果
     */
    @Override
    public boolean addOriginalChatMessageBatch(List<ChatHistoryOriginal> chatHistoryOriginals) {
        // 1、参数校验
        ThrowUtils.throwIf(chatHistoryOriginals == null || chatHistoryOriginals.size() == 0, ErrorCode.PARAMS_ERROR, "对话历史不能为空");
        List<ChatHistoryOriginal> validMessage = chatHistoryOriginals.stream().filter(chatHistoryOriginal -> {
            ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(chatHistoryOriginal.getMessageType());
            if (messageTypeEnum == null) {
                log.error("不支持该对话类型：{}", chatHistoryOriginal.getMessageType());
                return false; // 过滤掉无效消息
            }
            return true;
        }).collect(Collectors.toList());

        // 2、如果为空，直接返回
        if (validMessage.isEmpty()) {
            return false;
        }

        // 3、批量入库
        return this.saveBatch(validMessage);
    }

    /**
     * 根据 appId 关联删除对话历史记录
     * @param appId appId
     * @return 删除结果
     */
    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "appId不能为空");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId);
        return this.remove(queryWrapper);
    }

    /**
     * 将 APP 的对话历史加载到 缓存中（Redis）
     * @param appId appId
     * @param chatMemory 缓存
     * @param maxCount 最大数量
     * @return 加载结果
     */
    @Override
    public int loadOriginalChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount) {
        try {
            // 1、查询历史记录，考虑边缘记录类型
            List<ChatHistoryOriginal> originalHistoryList = queryHistoryWithEdgeCheck(appId, maxCount);
            if(CollUtil.isEmpty(originalHistoryList)) {
                return 0;
            }

            // 2、翻转列表，确保时间郑旭（老的在前，新的在后）
            originalHistoryList = originalHistoryList.reversed();

            // 3、先清理当前 app 的历史缓存，防止重复加载
            chatMemory.clear();

            // 4、遍历原始历史记录，根据类型将消息添加到记忆中
            int loadedCount = loadMessagesToMemory(originalHistoryList, chatMemory);
            log.info("成功为 appId：{} 加载 {} 条历史对话", appId, loadedCount);
            return loadedCount;
        } catch (Exception e) {
            log.error("加载历史对话失败：appId：{}。error：{}", appId, e.getMessage(), e);
            // 加载失败不影响系统运行，只是没有历史上下文
            return 0;
        }
    }


    /**
     * 查询历史记录，考虑边缘记录类型
     * 工具调用信息必须是成对并且有序的: tool_request -> tool_result，否则就会报错！
     * 错误信息：dev.langchain4j.exception.HttpException: {"error":{"message":"Messages with role 'tool' must be a response to a preceding message with 'tool_calls'","type":"invalid_request_error","param":null,"code":"invalid_request_error"}}
     *     1. 边缘检查的意义在于当查询到的第 maxCount + 1 那条数据是 tool_result 时就丢失了一条 tool_request，导致报错
     *     2. 这里改为了按 id 倒序查询，时间戳排序可能因为相近值而不稳定，当 tool_request 和 tool_result 的顺序加载错了会导致报错（MyBatis-flex的雪花算法生成的ID是严格递增的）
     *
     * @param appId 应用ID
     * @param maxCount 最大记录数
     * @return 历史记录列表
     */
    private List<ChatHistoryOriginal> queryHistoryWithEdgeCheck(Long appId, int maxCount) {
        // 1、检查总记录数
        QueryWrapper countQueryWrapper = QueryWrapper.create()
                .eq(ChatHistoryOriginal::getAppId, appId);
        long totalCount = this.count(countQueryWrapper);

        // 2、如果总记录数小于等于1，直接返回空数组(因为我们要跳过第一条记录，todo: 我感觉是用户只发了消息，AI并没有做回复，所以参考性不大)
        if(totalCount <= 1) {
            log.debug("总记录数：({}) 小于等于1，没有足够的对话记录可加载", totalCount);
            return Collections.emptyList();
        }

        // 3、计算实际可查询的最大记录数(减去要跳过的第一条记录， todo: why)
        // 原因：  - 第一条记录（最新的记录）通常是用户刚发送的消息
        //  - 这条消息已经在当前对话中，不需要从历史记录中加载
        //  - 因此跳过第一条，从第二条开始查询
        long availableCount = totalCount - 1;

        // todo: 这里不管大于小于的情况，都是会从 chatHistoryOriginal 表中查询 降序后的n条记录
        // 4、如果总记录数小于等于 maxCount+1，则不需要检查边缘记录
        if(totalCount <= maxCount + 1) {
            log.debug("总记录数 ({}) 小于等于 maxCount+1 ({})，不需要检查边缘记录", totalCount, maxCount + 1);

            // 直接查询所有可用记录（跳过最新的用户消息） todo: 是不是这里可以设置下最多 查询多少条
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistoryOriginal::getAppId, appId)
                    .orderBy(ChatHistoryOriginal::getId, false) // 使用id倒序，保证顺序性
                    .limit(1, availableCount); // 跳过用户最新发送的消息
            return this.list(queryWrapper);
        }

        // 5、如果总记录大于 maxCount+1，则需要检查边缘记录
        // why:查询第 maxCount + 1 条记录（便于记录）
        // 原因：如果 + 1的这条记录是 tool_result，那么就会丢失一条 tool_request，会导致 模型 无法解析。所以需要额外处理，确保工具调用的完整性
        QueryWrapper edgeQueryWrapper = QueryWrapper.create()
                .eq(ChatHistoryOriginal::getAppId, appId)
                .orderBy(ChatHistoryOriginal::getId, false)
                .limit(maxCount, 1); // 跳过 maxCount条消息，查询 第 maxCount + 1 条记录（todo：只有一条数据）

        // 获取边缘记录
        ChatHistoryOriginal edgeRecord = this.getOne(edgeQueryWrapper);

        // 6、如果边缘记录是 TOOL_EXECUTION_RESULT 类型，则需要额外查询一条 TOOL_EXECUTION_REQUEST 记录
        // 反之：如果 边缘记录是 TOOL_EXECUTION_REQUEST 类型，那么就可以确保 TOOL_EXECUTION_RESULT 也在 查询结果中
        boolean needExtraRequest = false;
        if(edgeRecord != null) {
            String edgeMessageType = edgeRecord.getMessageType();
            ChatHistoryMessageTypeEnum edgeMessageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(edgeMessageType);
            // 查询 边缘消息是否是 工具调用结果
            needExtraRequest = (edgeMessageTypeEnum == ChatHistoryMessageTypeEnum.TOOL_EXECUTION_RESULT);
        }

        // 7、计算实际需要查询的记录数 todo:不太懂
        // 原因：如果 needExtraRequest 是 true，那就说明 他是 TOOL_EXECUTION_RESULT， 可能缺少 TOOL_EXECUTION_REQUEST，需要 + 1
        // 否则，就是 maxCount
        long actualLimit = Math.min(needExtraRequest ? maxCount + 1 : maxCount, availableCount);

        // 8、查询历史记录 todo: 不太懂
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(ChatHistoryOriginal::getAppId, appId)
                .orderBy(ChatHistoryOriginal::getId, false)
                .limit(1, actualLimit); // 查询从第二条开始的 actualLimit 记录

        List<ChatHistoryOriginal> originalHistoryList = this.list(queryWrapper);
        if(CollUtil.isEmpty(originalHistoryList)) {
            return Collections.emptyList();
        }

        // 9、检查是否需要 调整 maxCount todo: 是不是 TOOL_EXECUTION_REQUEST 要和 TOOL_EXECUTION_RESULT 进行绑定，才好让 AI进行了解之前的对话
        if(needExtraRequest && originalHistoryList.size() <= maxCount) {
            // 如果需要额外的 TOOL_EXECUTION_REQUEST 记录，并且实际返回的记录数小于 maxCount，则需要将 maxCount 调整为实际返回的记录数减1
            log.warn("边缘记录是 TOOL_EXECUTION_RESULT 类型，单位获取到足够的记录包含 TOOL_EXECUTION_REQUEST，将 maxCount - 1");
            maxCount = Math.max(0, maxCount - 1); // 确保maxCount 不小于 0

            // 如果 maxCount 变为0，直接返回空列表
            if(maxCount == 0) {
                log.info("调整后 maxCount 为0，不加载任何历史记录");
                return Collections.emptyList();
            }

            // 重新查询，使用调整后的 maxCount
            actualLimit = Math.min(maxCount, availableCount);
            queryWrapper = QueryWrapper.create()
                    .eq(ChatHistoryOriginal::getAppId, appId)
                    .orderBy(ChatHistoryOriginal::getId, false)
                    .limit(1, actualLimit); // 查询从第二条开始的 actualLimit 记录

            originalHistoryList = this.list(queryWrapper);

            if(CollUtil.isEmpty(originalHistoryList)) {
                return Collections.emptyList();
            }
        }
        return originalHistoryList;
    }

    /**
     * 将历史记录加载到内存中
     *
     * @param originalHistoryList 历史记录列表
     * @param chatMemory 聊天记忆
     * @return 加载的记录数
     */
    private int loadMessagesToMemory(List<ChatHistoryOriginal> originalHistoryList, MessageWindowChatMemory chatMemory) {
        int loadCount = 0;
        // 遍历原始历史记录，根据类型将消息添加到记忆中
        for (ChatHistoryOriginal history : originalHistoryList) {
            // 这里需要根据消息类型进行转换，支持 AI, user, toolExecutionRequest, toolExecutionResult 4种类型
            String messageType = history.getMessageType();
            ChatHistoryMessageTypeEnum messageTypeEnum = ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
            switch (messageTypeEnum) {
                case USER:
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    break;
                case AI:
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    break;
                case TOOL_EXECUTION_REQUEST:
                    ToolRequestMessage toolRequestMessage = JSONUtil.toBean(history.getMessage(), ToolRequestMessage.class);
                    ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                            .id(toolRequestMessage.getId())
                            .name(toolRequestMessage.getName())
                            .arguments(toolRequestMessage.getArguments())
                            .build();
                    // 有些工具调用请求自带有文本，有些没有
                    if(toolRequestMessage.getText() == null || toolRequestMessage.getText().isEmpty()) {
                        chatMemory.add(AiMessage.from(List.of(toolExecutionRequest)));
                    } else {
                        chatMemory.add(AiMessage.from(toolRequestMessage.getText(), List.of(toolExecutionRequest)));
                    }
                    loadCount++;
                    break;
                case TOOL_EXECUTION_RESULT:
                    ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(history.getMessage(), ToolExecutedMessage.class);
                    String id = toolExecutedMessage.getId();
                    String toolName = toolExecutedMessage.getName();
                    // 注意：ToolExecutionResultMessage.from() 需要的是工具执行结果，而不是 arguments
                    // arguments 是工具请求的参数，result 才是工具执行的结果
                    String toolExecutionResult = toolExecutedMessage.getResult() != null ? toolExecutedMessage.getResult() : toolExecutedMessage.getArguments();
                    chatMemory.add(ToolExecutionResultMessage.from(id, toolName, toolExecutionResult));
                    loadCount++;
                    break;
                default:
                    log.warn("未知的消息类型: {}", messageType);
                    break;
            }
        }
        return loadCount;
    }

}
