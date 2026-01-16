package com.wjp.waicodermotherbackend.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorService;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorServiceFactory;
import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
import com.wjp.waicodermotherbackend.ai.model.message.AIResponseMessage;
import com.wjp.waicodermotherbackend.ai.model.message.ToolExecutedMessage;
import com.wjp.waicodermotherbackend.ai.model.message.ToolRequestMessage;
import com.wjp.waicodermotherbackend.constant.AppConstant;
import com.wjp.waicodermotherbackend.core.builder.VueProjectBuilder;
import com.wjp.waicodermotherbackend.core.parser.CodeParserExecutor;
import com.wjp.waicodermotherbackend.core.saver.CodeFileSaverExecutor;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成门面模式：组合生成和保存代码功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    /**
     *  AI 代码生成服务 [AI Service都是固定的]
     */
//    @Resource
//    private AiCodeGeneratorService aiCodeGeneratorService;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    // region 单/多文件保存
    /**
     * 生成代码并保存
     * @param userMessage 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, int version) {
        if(StrUtil.isEmpty(userMessage)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户提示词不能为空");
        }
        if(codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }

        // 根据 appId 获取对应的 AI 代码生成服务
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);

        return switch(codeGenTypeEnum) {
            case HTML -> {
                // 1、先通过 AI 生成 代码
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                // 2、在保存文件
                yield CodeFileSaverExecutor.saveCode(htmlCodeResult, codeGenTypeEnum, appId, version);
            }
            case MULTI_FILE -> {
                // 1、先通过 AI 生成 代码
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                // 2、在保存文件
                yield CodeFileSaverExecutor.saveCode(multiFileCodeResult, codeGenTypeEnum, appId, version);
            }
            default -> {
                String errStr = "不支持的代码生成类型:" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errStr);
            }
        };
    }

    // endregion


    // region 单/多文件SSE保存
    /**
     * 统一入口：根据类型生成并保存代码
     * @param userMessage 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, int version) {
        if(StrUtil.isEmpty(userMessage)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户提示词不能为空");
        }
        if(codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }

        // 根据 appId 获取对应的 AI 代码生成服务
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);

        return switch(codeGenTypeEnum) {
            case HTML -> {
                // 1、调用Ai获取流式返回的数据
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                // 2、处理代码流
                yield processCodeStream(result, CodeGenTypeEnum.HTML, appId, version);
            }
            case MULTI_FILE -> {
                // 1、调用Ai获取流式返回的数据
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                // 2、处理代码流
                yield processCodeStream(result, CodeGenTypeEnum.MULTI_FILE, appId, version);
            }
            case VUE_PROJECT -> {
                // 1、调用Ai获取流式返回的数据
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                // 2、处理代码流
                // 注意：VUE_PROJECT 类型在流式生成过程中已经通过 FileWriteTool 实时写入文件，所以不需要在流式完成后再次保存
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errStr = "不支持的代码生成类型:" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errStr);
            }
        };
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     * @param tokenStream TokenStream 对象
     * @return Flux<String>
     *
     * description:
     *  1、AI 文本响应片段 -> 回调：onPartialResponse，参数: AIResponseMessage
     *  2、工具调用请求 -> 回调: onPartialToolExecutionRequest，参数：ToolRequestMessage
     *  3、工具执行完毕结果 -> 回调：onToolExecuted，参数：ToolExecutedMessage
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        // create: 手动创建响应式流
        // sink: 向订阅者发送数据
        return Flux.create(sink -> {
            // onPartialResponse: 当AI返回部分文本时触发
            // partialResponse: Ai返回的部分文本
            tokenStream.onPartialResponse((String partialResponse) -> {
                // 创建 AI响应消息对象
                AIResponseMessage aiResponseMessage = new AIResponseMessage(partialResponse);
                // 发送JSON到流
                sink.next(JSONUtil.toJsonStr(aiResponseMessage));
            })
            // onPartialToolExecutionRequest: 注册工具执行请求回调
            // index: 请求索引
            // toolExecutionRequest: 工具执行请求对象
            .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                // 创建 工具请求消息对象
                ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                // 发送JSON到流
                sink.next(JSONUtil.toJsonStr(toolRequestMessage));
            })
            // onToolExecuted: 注册工具执行完成回调
            // toolExecution: 包含工具执行请求和结果
            .onToolExecuted((ToolExecution toolExecution) -> {
                // 创建 工具执行结果消息对象
                ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
            })
            // onCompleteResponse: 注册完整响应回调
            // chatResponse: 完整的聊天响应对象
            .onCompleteResponse((ChatResponse chatResponse) -> {
                // 同步构建 Vue项目
                String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + "/vue_project_" + appId;
                vueProjectBuilder.buildProject(projectPath);
                // 完成流
                sink.complete();
            })
            // onError: 注册 错误回调
            .onError((Throwable error) -> {
                error.printStackTrace();
                sink.error(error);
            })
            .start();
        });
    }

    /**
     * 通用流式代码处理方法
     * @param codeStream 代码流
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存的目录
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum, Long appId, int version) {
        // 2、字符串拼接(因为流式输出他是一块一块的，所以需要拼接)
        StringBuilder codeBuilder = new StringBuilder();
        // 3、返回Flux数据
        return codeStream
                .doOnNext(chunk -> {
                    // 实时收集代码块
                    codeBuilder.append(chunk);
                })
                .doOnCancel(() -> {
                    // 当客户端断开连接时，Flux 会被取消
                    log.warn("【代码流被取消】appId: {}, version: {}, 代码生成已中断，已收集代码长度: {}", 
                            appId, version, codeBuilder.length());
                })
                .doOnComplete(() -> {
                    // VUE_PROJECT 类型在流式生成过程中已经通过 FileWriteTool 实时写入文件，不需要再次保存
                    if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
                        log.info("【代码流完成】appId: {}, version: {}, VUE_PROJECT 类型，文件已通过工具调用实时写入，无需再次保存", 
                                appId, version);
                        return;
                    }
                    
                    long startTime = System.currentTimeMillis();
                    log.info("【代码流完成】appId: {}, version: {}, 开始保存代码，已收集代码长度: {}", 
                            appId, version, codeBuilder.length());
                    
                    try {
                        // 流式输出完成后，保存代码
                        // 转为String
                        String completeCode = codeBuilder.toString();
                        // 使用解析器代码为对象
                        Object multiFileCodeResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                        // 使用保存器保存代码
                        File file = CodeFileSaverExecutor.saveCode(multiFileCodeResult, codeGenTypeEnum, appId, version);
                        long endTime = System.currentTimeMillis();
                        log.info("【代码保存成功】appId: {}, version: {}, 目录: {}, 耗时: {}ms", 
                                appId, version, file.getAbsolutePath(), (endTime - startTime));
                    } catch(Exception e) {
                        long endTime = System.currentTimeMillis();
                        log.error("【代码保存失败】appId: {}, version: {}, 耗时: {}ms", 
                                appId, version, (endTime - startTime), e);
                    }
                })
                .doOnError(error -> {
                    log.error("【代码流错误】appId: {}, version: {}, 发生错误", appId, version, error);
                });
    }


    // endregion





}
