package com.wjp.waicodermotherbackend.ai;

import com.wjp.waicodermotherbackend.utils.SpringContextUtil;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 代码生成类型智能路由服务工厂
 */
@Configuration
@Slf4j
public class AiCodeGenTypeRoutingServiceFactory {

//    @Resource
//    private ChatModel chatModel;

    /**
     * 创建 AI 代码生成类型路由服务实例
     * @return
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        // 使用多例模式解决并发问题
        ChatModel chatModel = SpringContextUtil.getBean("routingChatModelPrototype", ChatModel.class);
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }

}
