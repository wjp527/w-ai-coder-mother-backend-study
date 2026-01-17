package com.wjp.waicodermotherbackend.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wjp.waicodermotherbackend.ai.guardrail.PromptSafetyInputGuardrail;
import com.wjp.waicodermotherbackend.ai.guardrail.RetryOutputGuardrail;
import com.wjp.waicodermotherbackend.ai.tools.*;
import com.wjp.waicodermotherbackend.config.ReasoningStreamingChatModelConfig;
import com.wjp.waicodermotherbackend.config.RedisChatMemoryStoreConfig;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.entity.ChatHistoryOriginal;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
import com.wjp.waicodermotherbackend.service.ChatHistoryOriginalService;
import com.wjp.waicodermotherbackend.service.ChatHistoryService;
import com.wjp.waicodermotherbackend.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 服务创建工厂
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    // 指定注入 openAiChatModel 模型
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

//    /**
//     *deepseek-chat 模型
//      */
//    @Resource
//    private StreamingChatModel openAiStreamingChatModel;
//
//    /**
//     * deepseek-reasoner 模型
//     */
//    @Resource
//    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 对话记忆【包含工具调用】
     */
    @Resource
    private ChatHistoryOriginalService chatHistoryOriginalService;

    /**
     * 工具实例
     */
    @Resource
    private ToolManager toolManager;


    /**
     * AI 服务实例缓存
     * 缓存策略:
     * - 最大缓存 1000 哥实例
     * - 写入后 30min 过期
     * - 访问后 10min 过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除, 缓存键: {}, 原因: {}", key, cause);
            }).build();


    /**
     * 根据 appId 获取服务 (为了兼容老的逻辑)
     * @param appId 应用ID
     * @return AI服务
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(Long appId) {
        // appId有值，则从缓存中获取
        // 如果没有，则创建
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据 appId 获取服务
     * @Param appId 应用ID
     * @Param codeGenType 代码生成类型
     * @return  AI Service
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(Long appId, CodeGenTypeEnum codeGenType) {
        String cacheKey = buildCacheKey(appId, codeGenType);
        // appId有值，则从缓存中获取
        // 如果没有，则创建
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 创建 AI 服务 todo：为什么这里的方法没有触发
     * @Param appId 应用ID
     * @Param codeGenType 代码生成类型
     * @return  AI Service
     */
   public AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType) {
       log.info("为 appId {} 创建新的 AI Service 服务实例", appId);
       AiCodeGeneratorService aiCodeGeneratorService;
       // 根据 appId 构建独立的对话记忆
       MessageWindowChatMemory chatMemory = MessageWindowChatMemory
               .builder()
               .id(appId)
               .chatMemoryStore(redisChatMemoryStore)
               // 设置的太小，会导致模型陷入死循环，重复写入相同的文件
               .maxMessages(50)
               .build();


       // 根据代码生成类型选择不同的模型配置
       switch(codeGenType) {
           // Vue项目生成使用推理模型
           case VUE_PROJECT -> {
               // 使用多例模式解决并发问题
               // 之所以不用之前 @Resource定义的模型，是因为他只会初始化一次，后面就会一直用这个实例，那么后面的所有请求都会用同一个实例，并发下会产生冲突
               // 而在这里每次调用都会重新获取实例，所以就不会导致冲突问题了
               StreamingChatModel reasoningStreamingChatModel = SpringContextUtil.getBean("reasoningStreamingChatModelPrototype", StreamingChatModel.class);
               // 从数据库加载历史对话到缓存中，由于多了工具调用相关信息，加载的最大数量稍微大些
               chatHistoryOriginalService.loadOriginalChatHistoryToMemory(appId, chatMemory, 50);
               aiCodeGeneratorService = AiServices.builder(AiCodeGeneratorService.class)
                       .chatModel(chatModel) // 普通模型
                       .streamingChatModel(reasoningStreamingChatModel) // 流式模型
                       // 这里生成Vue项目的时候会调用工具，工具调用了memoryId，所以这里必须指定 chatMemoryProvider
                       .chatMemoryProvider(memoryId -> chatMemory) // 对话记忆
                       // 绑定工具
                       .tools(toolManager.getAllTools())
                       // 最大调用工具的次数
                       .maxSequentialToolsInvocations(20)
                       // 处理工具调用幻觉问题，出现幻觉，进入错误处理
                       .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                               toolExecutionRequest, "Error: there is no tool called" + toolExecutionRequest.name()
                       ))
                       // 输入护轨
                       .inputGuardrails(new PromptSafetyInputGuardrail())
                       // 输出护轨 为了流式输出，这里不使用，因为开启护轨，流式输出会失效
                       // .outputGuardrails(new RetryOutputGuardrail())
                       .build();
           }
           // HTML 和 多文件生成使用默认模型
           case HTML, MULTI_FILE -> {
               // 使用多例模式解决并发问题
               // 之所以不用之前 @Resource定义的模型，是因为他只会初始化一次，后面就会一直用这个实例，那么后面的所有请求都会用同一个实例，并发下会产生冲突
               // 而在这里每次调用都会重新获取实例，所以就不会导致冲突问题了
               StreamingChatModel openAiStreamingChatModel = SpringContextUtil.getBean("streamingChatModelPrototype", StreamingChatModel.class);
               // 加载历史会话记录
               chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
               aiCodeGeneratorService = AiServices.builder(AiCodeGeneratorService.class)
                       .chatModel(chatModel) // 普通模型
                       .streamingChatModel(openAiStreamingChatModel) // 流式模型
                       .chatMemory(chatMemory) // 对话记忆
                       // 最大调用工具的次数
                       .maxSequentialToolsInvocations(20)
                       // 输入护轨
                       .inputGuardrails(new PromptSafetyInputGuardrail())
                       // 输出护轨 为了流式输出，这里不使用，因为开启护轨，流式输出会失效
                       // .outputGuardrails(new RetryOutputGuardrail())
                       .build();
           }
           default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                   "不支持的代码生成类型: " + codeGenType.getValue());
       };
       return aiCodeGeneratorService;
    }


    /**
     * 创建AI代码生成服务
     * @return
     */
    @Bean
    public AiCodeGeneratorService create() {
        // 默认使用 appId = 0
        return getAiCodeGeneratorService(0L);

//        仅使用普通同步 ChatModel（不启用 SSE 流式输出）
//        return AiServices.create(
//                AiCodeGeneratorService.class,
//                chatModel
//        );

        // 携带SSE流式模型
//        return AiServices.builder(AiCodeGeneratorService.class)
//                .chatModel(chatModel) // 普通模型
//                .streamingChatModel(streamingChatModel) // 流式模型
//                // 根据 id 构建独立的对话记忆
//                .chatMemoryProvider(memoryId -> MessageWindowChatMemory
//                        .builder()
//                        .id(memoryId)
//                        .chatMemoryStore(redisChatMemoryStore)
//                        .maxMessages(20)
//                        .build())
//                .build();
    }


    /**
     * 构建缓存键
     * @param appId   应用ID
     * @param codeGenType 代码生成类型
     * @return 缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

}
