package com.wjp.waicodermotherbackend.langgraph4j.ai;

import com.wjp.waicodermotherbackend.langgraph4j.model.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Value;

/**
 * 代码质量检测服务
 */
public interface CodeQualityCheckService {

    /**
     * 检查代码质量
     * AI 会分析代码并返回指令检查结果
     * @param codeContent 代码内容
     * @return 代码质量检测结果
     */
    @SystemMessage(fromResource = "wjp-ai-code-ai/src/main/resources/prompt/code-quality-check-system-prompt.txt")
    QualityResult checkCodeQuality(@UserMessage String codeContent);

}
