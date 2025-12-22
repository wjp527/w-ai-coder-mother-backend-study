package com.wjp.waicodermotherbackend.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * AI Service
 */
public interface AiCodeGeneratorService {

    /**
     * 生成HTML代码
     * @param userMessage 用户的提示词
     * @return AI 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    String generateHtmlCode(@UserMessage String userMessage);

    /**
     * 生成多文件代码
     * @param userMessage 用户的提示词
     * @return AI 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    String generateMultiFileCode(@UserMessage String userMessage);

}
