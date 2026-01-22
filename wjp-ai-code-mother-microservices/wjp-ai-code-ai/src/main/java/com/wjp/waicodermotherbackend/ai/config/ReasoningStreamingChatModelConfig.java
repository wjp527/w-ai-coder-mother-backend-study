package com.wjp.waicodermotherbackend.ai.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * 推理流式模型配置
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@Data
public class ReasoningStreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Boolean logRequests = false;

    private Boolean logResponses = false;


    /**
     * 推理流式模型 (用于 Vue 项目生成，带工具调用)
     */
    @Bean
    // Prototype scope: 每次从 Spring 容器获取都会创建新的 StreamingChatModel 实例。
    // 用于避免在并发/流式调用场景下共享同一实例可能带来的状态冲突/线程安全问题。
    // 注意：只有“每次获取一次 Bean”才会新建；若注入到 singleton 字段里仍可能只创建一次（需用 ObjectProvider/Provider 按需获取）。
    @Scope("prototype")
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
