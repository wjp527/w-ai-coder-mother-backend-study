package com.wjp.waicodermotherbackend.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 服务创建工厂
 */
@Configuration
public class AiCodeGeneratorServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    /**
     * 创建AI代码生成服务
     * @return
     */
    @Bean
    public AiCodeGeneratorService create() {
//        仅使用普通同步 ChatModel（不启用 SSE 流式输出）
//        return AiServices.create(
//                AiCodeGeneratorService.class,
//                chatModel
//        );

        // 携带SSE流式模型
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel) // 普通模型
                .streamingChatModel(streamingChatModel) // 流式模型
                .build();
    }

}
