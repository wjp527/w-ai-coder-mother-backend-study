package com.wjp.waicodermotherbackend.langgraph4j.node;

import com.wjp.waicodermotherbackend.langgraph4j.ai.ImageCollectionPlanService;
import com.wjp.waicodermotherbackend.langgraph4j.ai.ImageCollectionService;
import com.wjp.waicodermotherbackend.langgraph4j.model.ImageCollectionPlan;
import com.wjp.waicodermotherbackend.langgraph4j.model.enums.ImageCategoryEnum;
import com.wjp.waicodermotherbackend.langgraph4j.model.ImageResource;
import com.wjp.waicodermotherbackend.langgraph4j.state.WorkflowContext;
import com.wjp.waicodermotherbackend.langgraph4j.tools.ImageSearchTool;
import com.wjp.waicodermotherbackend.langgraph4j.tools.LogoGeneratorTool;
import com.wjp.waicodermotherbackend.langgraph4j.tools.MermaidDiagramTool;
import com.wjp.waicodermotherbackend.langgraph4j.tools.UndrawIllustrationTool;
import com.wjp.waicodermotherbackend.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            List<ImageResource> collectedImages = new ArrayList<>();
            // String imageListStr = "";

            try {
                // 第一步：获取图片收集服务
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                // 获取图片收集计划
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("获取到图片收集计划，开始并发执行");

                // 第二步：并发执行各种图片收集人物
                // CompletableFuture: 是存放异步任务执行结果的容器
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                // 并发执行内容图片搜索
                if(plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        // 将 task 赋值给局部变量，避免 lambda 闭包问题
                        // CompletableFuture.supplyAsync() 会异步执行 lambda 中的代码【就像下单，返回订单号CompletableFuture，这个时候就会开始执行任务了】
                        // 这样多个搜索任务可以并发执行，而不是串行等待
                        futures.add(CompletableFuture.supplyAsync(() ->
                                imageSearchTool.searchContentImages(task.query())));
                    }
                }

                // 并发执行插画图片搜索
                if(plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool undrawIllustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                undrawIllustrationTool.searchIllustrations(task.query())));
                    }
                }

                // 并发执行架构图生成
                if(plan.getDiagramTasks() != null) {
                    MermaidDiagramTool mermaidDiagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                mermaidDiagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                    }
                }

                // 并发执行LOGO图生成
                if(plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoGeneratorTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoGeneratorTool.generateLogos(task.description())));
                    }
                }

                // 等待所有任务完成并收集结果
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture[0])
                );
                // 等待所有任务完成
                allTasks.join();
                // 收集所有结果
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.get();
                    if(images != null) {
                        collectedImages.addAll(images);
                    }
                }
                log.info("并发图片收集完成，共收集到{}张图片", collectedImages.size());

            } catch (Exception e) {
                log.error("图片收集失败：{}", e.getMessage(), e);
            }

            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            // 保存状态，用于后续节点使用
            return WorkflowContext.saveContext(context);
        });
    }
}
