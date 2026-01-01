package com.wjp.waicodermotherbackend.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.wjp.waicodermotherbackend.config.RedisChatMemoryStoreConfig;
import com.wjp.waicodermotherbackend.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
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

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;


    /**
     * AI 服务实例缓存
     * 缓存策略:
     * - 最大缓存 1000 哥实例
     * - 写入后 30min 过期
     * - 访问后 10min 过期
     */
    private final Cache<Long, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AI 服务实例被移除, appId: {}, 原因: {}", key, cause);
            }).build();


    /**
     * 根据 appId 获取服务
     * @param appId 应用ID
     * @return AI服务
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(Long appId) {
        // appId有值，则从缓存中获取
        // 如果没有，则创建
        return serviceCache.get(appId, this::createAiCodeGeneratorService);
    }

    /**
     * 创建 AI 服务
     */
   public AiCodeGeneratorService createAiCodeGeneratorService(long appId) {
       // 根据 appId 构建独立的对话记忆
       MessageWindowChatMemory chatMemory = MessageWindowChatMemory
               .builder()
               .id(appId)
               .chatMemoryStore(redisChatMemoryStore)
               .maxMessages(20)
               .build();

       // 加载历史会话记录
       chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);

       return AiServices.builder(AiCodeGeneratorService.class)
               .chatModel(chatModel) // 普通模型
               .streamingChatModel(streamingChatModel) // 流式模型
               .chatMemory(chatMemory) // 对话记忆
               .build();
    }


    /**
     * 创建AI代码生成服务
     * @return
     */
    @Bean
    public AiCodeGeneratorService create() {
        // 默认使用 appId = 0
        return createAiCodeGeneratorService(0);

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

}
