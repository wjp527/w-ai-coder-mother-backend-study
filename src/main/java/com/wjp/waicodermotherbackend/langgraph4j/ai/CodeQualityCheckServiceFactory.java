package com.wjp.waicodermotherbackend.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 代码质量检测服务工厂
 */
@Slf4j
@Configuration
public class CodeQualityCheckServiceFactory {
    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    /**
     * 创建代码指令检查AI服务
     */
    @Bean
    public CodeQualityCheckService createCodeQualityCheckService() {
        return AiServices.builder(CodeQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }
}
