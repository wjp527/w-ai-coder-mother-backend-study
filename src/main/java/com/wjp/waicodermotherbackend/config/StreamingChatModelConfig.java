package com.wjp.waicodermotherbackend.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * 多例流式对话模型配置
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer maxTokens;
    private Double temperature;
    private boolean logRequests;
    private boolean logResponses;

    /**
     * 非推理流式模型
     */
    @Bean
    @Scope("prototype")
    // Prototype scope: 每次从 Spring 容器获取都会创建新的 StreamingChatModel 实例。
    // 用于避免在并发/流式调用场景下共享同一实例可能带来的状态冲突/线程安全问题。
    // 注意：只有“每次获取一次 Bean”才会新建；若注入到 singleton 字段里仍可能只创建一次（需用 ObjectProvider/Provider 按需获取）。
    public StreamingChatModel streamingChatModelPrototype() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens )
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

}
