package com.wjp.waicodermotherbackend.langgraph4j.node;

import com.wjp.waicodermotherbackend.langgraph4j.ai.ImageCollectionService;
import com.wjp.waicodermotherbackend.langgraph4j.model.enums.ImageCategoryEnum;
import com.wjp.waicodermotherbackend.langgraph4j.model.ImageResource;
import com.wjp.waicodermotherbackend.langgraph4j.state.WorkflowContext;
import com.wjp.waicodermotherbackend.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点
 */
@Slf4j
public class ImageCollectorNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片收集");
            
            // TODO: 实际执行图片收集逻辑
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = "";

            try {
                // 获取 AI图片收集服务
                // 之所以不用@Resource注解，是因为 静态方法无法直接通过 @Resource 注解注入依赖
                ImageCollectionService imageCollectionService = SpringContextUtil.getBean(ImageCollectionService.class);
                // 使用AI服务进行智能图片搜集
                imageListStr = imageCollectionService.collectImages(originalPrompt);
            } catch (Exception e) {
                log.error("图片收集失败：{}", e.getMessage(), e);
            }

            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageListStr(imageListStr);
            // 保存状态，用于后续节点使用
            return WorkflowContext.saveContext(context);
        });
    }
}
