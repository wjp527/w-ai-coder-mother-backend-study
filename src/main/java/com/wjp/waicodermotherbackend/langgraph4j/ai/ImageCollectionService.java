package com.wjp.waicodermotherbackend.langgraph4j.ai;

import com.wjp.waicodermotherbackend.langgraph4j.model.ImageResource;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

import java.util.List;

/**
 * 图片收集 AI 服务接口
 * 使用 AI 调用工具收集不同类型的图片资源
 */
public interface ImageCollectionService {

    /**
     * 根据用户提示词收集所需的图片资源
     * AI 会自动根据需求自主选择对应的工具
     * @param userPrompt 用户提示词
     * @return 图片资源列表
     */
    @SystemMessage(fromResource = "wjp-ai-code-ai/src/main/resources/prompt/image-collection-system-prompt.txt")
    String collectImages(@UserMessage String userPrompt);

}
