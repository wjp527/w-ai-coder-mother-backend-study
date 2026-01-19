package com.wjp.waicodermotherbackend.monitor;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import jakarta.annotation.Resource;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * AI模型监听器
 */
@Component
@Slf4j
public class AiModelMonitorListener implements ChatModelListener {

    // 用于存储请求开始时间的 key
    private static final String REQUEST_START_TIME_KEY = "request_start_time";
    // 用于监控上下文传递（因为请求和响应事件的触发不是同一个线程）
    private static final String MONITOR_CONTEXT_KEY = "monitor_context";


    @Resource
    private AiModelMetricsCollector aiModelMetricsCollector;

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 记录请求开始时间
        requestContext.attributes().put(REQUEST_START_TIME_KEY, Instant.now());
        // 从监控上下文中获取信息
        MonitorContext context = MonitorContextHolder.getContext();
        String userId = context.getUserId();
        String appId = context.getAppId();
        // todo：不懂为什么还要将 监控上下文传递给 AI模型监听器
        // 因为：请求和响应的线程不一样？
        requestContext.attributes().put(MONITOR_CONTEXT_KEY, context);
        // 获取模型名称
        String modelName = requestContext.chatRequest().modelName();
        // 记录请求指标
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "started");

    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        // 从属性中获取监控信息（由 onRequest 方法存储）
        Map<Object, Object> attributes = responseContext.attributes();
        // 从监控上下文中获取信息
        MonitorContext context = (MonitorContext) attributes.get(MONITOR_CONTEXT_KEY);
        String userId = context.getUserId();
        String appId = context.getAppId();
        // 获取模型名称
        String modelName = responseContext.chatRequest().modelName();
        // 记录成功请求
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "success");
        // 记录响应时间
        recordResponseTime(attributes, userId, appId, modelName);
        // 记录 Token 使用情况
        recordTokenUsage(responseContext, userId, appId, modelName);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        // 从监控上下文中获取信息
        MonitorContext context = MonitorContextHolder.getContext();
        String userId = context.getUserId();
        String appId = context.getAppId();
        // 获取模型名称和错误类型
        String modelName = errorContext.chatRequest().modelName();
        String errorMessage = errorContext.error().getMessage();
        // 记录失败请求
        aiModelMetricsCollector.recordRequest(userId, appId, modelName, "error");
        aiModelMetricsCollector.recordError(userId, appId, modelName, errorMessage);
        // 记录响应时间（即使是错误响应）
        Map<Object, Object> attributes = errorContext.attributes();
        recordResponseTime(attributes, userId, appId, modelName);


    }

    /**
     * 记录响应时间
     * @param attributes 属性
     * @param userId 用户Id
     * @param appId 应用Id
     * @param modelName 模型名称
     */
    private void recordResponseTime(Map<Object, Object> attributes, String userId, String appId, String modelName) {
        Instant startTime = (Instant) attributes.get(REQUEST_START_TIME_KEY);
        Duration responseTime = Duration.between(startTime, Instant.now());
        aiModelMetricsCollector.recordResponseTime(userId, appId, modelName, responseTime);
    }

    /**
     * 记录 Token 使用情况
     * @param responseContext 响应上下文
     * @param userId 用户Id
     * @param appId  应用Id
     * @param modelName 模型名称
     */
    private void recordTokenUsage(ChatModelResponseContext responseContext, String userId, String appId, String modelName) {
        TokenUsage tokenUsage = responseContext.chatResponse().metadata().tokenUsage();
        if(tokenUsage != null) {
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "input", tokenUsage.inputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "output", tokenUsage.outputTokenCount());
            aiModelMetricsCollector.recordTokenUsage(userId, appId, modelName, "total", tokenUsage.totalTokenCount());
        }
    }
}
