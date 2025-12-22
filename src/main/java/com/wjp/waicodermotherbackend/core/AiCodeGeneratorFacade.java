package com.wjp.waicodermotherbackend.core;

import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorService;
import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * AI 代码生成门面模式：组合生成和保存代码功能
 */
@Service
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
