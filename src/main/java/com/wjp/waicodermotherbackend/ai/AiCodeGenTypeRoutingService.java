package com.wjp.waicodermotherbackend.ai;

import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * AI代码生成类型智能路由服务
 * 使用机构化输出直接返回枚举类型
 * @author wjp
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户需求智能选择代码生成类型
     * @param userPrompt 用户需求
     * @return 代码生成类型枚举
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);

}
