package com.wjp.waicodermotherbackend.ai;

import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
    HtmlCodeResult generateHtmlCode(@UserMessage String userMessage);

    /**
     * 生成多文件代码
     * @param userMessage 用户的提示词
     * @return AI 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(@UserMessage String userMessage);


    // region 流式输出(单/多文件)
    /**
     * 生成HTML代码
     * @param userMessage 用户的提示词
     * @return AI 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(@UserMessage String userMessage);

    /**
     * 生成多文件代码
     * @param userMessage 用户的提示词
     * @return AI 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(@UserMessage String userMessage);

    // endregion


    // region Vue项目

    /**
     * 生成 Vue项目代码 (流式)
     * @param appId 为了工具调用的时候可以获取到appId，所以就需要在调用AI的时候传递
     * @param userMessage 用户的提示词
     * @return AI 生成的代码
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

    // endregion










}
