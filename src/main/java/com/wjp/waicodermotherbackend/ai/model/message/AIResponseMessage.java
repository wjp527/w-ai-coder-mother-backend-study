package com.wjp.waicodermotherbackend.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 响应消息
 */
@EqualsAndHashCode(callSuper = true) // 自动生成 equals 和 hashCode 方法
@Data
@NoArgsConstructor // 生成无参构造函数
public class AIResponseMessage extends StreamMessage{
    private String data;

    public AIResponseMessage(String data) {
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.data = data;
    }
}
