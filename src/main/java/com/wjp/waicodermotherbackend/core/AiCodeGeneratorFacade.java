package com.wjp.waicodermotherbackend.core;

import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorService;
import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
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
            case HTML -> generateAndSaveHtmlCode(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCode(userMessage);
            default -> {
                String errStr = "不支持的代码生成类型:" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errStr);
            }
        };
    }

    // region 单/多文件SSE保存
    /**
     * 生成代码并保存 (流式输出)
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
            case HTML -> generateAndSaveHtmlCodeStream(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCodeStream(userMessage);
            default -> {
                String errStr = "不支持的代码生成类型:" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errStr);
            }
        };
    }

    /**
     * 生成 HTML 代码并保存 (流式输出)
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage) {
        // 1、调用Ai获取流式返回的数据
        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
        // 2、字符串拼接(因为流式输出他是一块一块的，所以需要拼接)
        StringBuilder codeBuilder = new StringBuilder();
        // 3、返回Flux数据
        return result.doOnNext(chunk -> {
            // 实时收集代码块
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            try {
                // 流式输出完成后，保存代码
                // 转为String
                String codeContent = codeBuilder.toString();
                // 解析代码为对象
                HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(codeContent);
                // 保存文件
                File file = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
                log.info("保存成功，目录为:{}", file.getAbsolutePath());
            } catch(Exception e) {
                log.error("保存代码失败", e);
            }
        });
    }

    /**
     * 生成 多文件 代码并保存 (流式输出)
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage) {
        // 1、调用Ai获取流式返回的数据
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        // 2、字符串拼接(因为流式输出他是一块一块的，所以需要拼接)
        StringBuilder codeBuilder = new StringBuilder();
        // 3、返回Flux数据
        return result.doOnNext(chunk -> {
            // 实时收集代码块
            codeBuilder.append(chunk);
        }).doOnComplete(() -> {
            try {
                // 流式输出完成后，保存代码
                // 转为String
                String codeContent = codeBuilder.toString();
                // 解析代码为对象
                MultiFileCodeResult multiFileCodeResult = CodeParser.parseMultiFileCode(codeContent);;
                // 保存文件
                File file = CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
                log.info("保存成功,目录为:{}", file.getAbsolutePath());
            } catch(Exception e) {
                log.error("保存代码失败", e);
            }
        });
    }

    // endregion

    /**
     * 生成 HTML 代码并保存
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateAndSaveHtmlCode(String userMessage) {
        // 1、先通过 AI 生成 代码
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
        // 2、在保存文件
        File file = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
        return file;
    }

    /**
     * 生成 多文件 代码并保存
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateAndSaveMultiFileCode(String userMessage) {
        // 1、先通过 AI 生成 代码
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        // 2、在保存文件
        File file = CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
        return file;
    }

}
