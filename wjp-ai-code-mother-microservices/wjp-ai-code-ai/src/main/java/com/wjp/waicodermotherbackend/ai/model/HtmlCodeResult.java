package com.wjp.waicodermotherbackend.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * HTML代码结果
 */
@Data
@Description("HTML代码结果")
public class HtmlCodeResult {

    // HTML代码
    @Description("HTML代码")
    private String htmlCode;

    // 描述
    @Description("生成描述")
    private String description;
}
