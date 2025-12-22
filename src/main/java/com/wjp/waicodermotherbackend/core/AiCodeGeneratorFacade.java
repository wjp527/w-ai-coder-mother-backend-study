package com.wjp.waicodermotherbackend.core;

import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorService;
import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
import com.wjp.waicodermotherbackend.core.parser.CodeParserExecutor;
import com.wjp.waicodermotherbackend.core.saver.CodeFileSaverExecutor;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
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
     *  AI 代码生成服务
     */
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    // region 单/多文件保存
    /**
     * 生成代码并保存
     * @param userMessage 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if(StrUtil.isEmpty(userMessage)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户提示词不能为空");
        }
        if(codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }

        return switch(codeGenTypeEnum) {
            case HTML -> {
                // 1、先通过 AI 生成 代码
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                // 2、在保存文件
                yield CodeFileSaverExecutor.saveCode(htmlCodeResult, codeGenTypeEnum);
            }
            case MULTI_FILE -> {
                // 1、先通过 AI 生成 代码
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                // 2、在保存文件
                yield CodeFileSaverExecutor.saveCode(multiFileCodeResult, codeGenTypeEnum);
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
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if(StrUtil.isEmpty(userMessage)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户提示词不能为空");
        }
        if(codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }

        return switch(codeGenTypeEnum) {
            case HTML -> {
                // 1、调用Ai获取流式返回的数据
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                // 2、处理代码流
                yield  processCodeStream(result, CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE -> {
                // 1、调用Ai获取流式返回的数据
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                // 2、处理代码流
                yield  processCodeStream(result, CodeGenTypeEnum.MULTI_FILE);
            }
            default -> {
                String errStr = "不支持的代码生成类型:" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errStr);
            }
        };
    }

    /**
     * 通用流式代码处理方法
     * @param codeStream 代码流
     * @param codeGenTypeEnum 代码生成类型
     * @return 保存的目录
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum) {
        // 2、字符串拼接(因为流式输出他是一块一块的，所以需要拼接)
        StringBuilder codeBuilder = new StringBuilder();
        // 3、返回Flux数据
        return codeStream.doOnNext(chunk -> {
            // 实时收集代码块
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            try {
                // 流式输出完成后，保存代码
                // 转为String
                String completeCode = codeBuilder.toString();
                // 使用解析器代码为对象
                Object multiFileCodeResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                // 使用保存器保存代码
                File file = CodeFileSaverExecutor.saveCode(multiFileCodeResult, codeGenTypeEnum);
                log.info("保存成功,目录为:{}", file.getAbsolutePath());
            } catch(Exception e) {
                log.error("保存代码失败", e);
            }
        });
    }


    // endregion





}
