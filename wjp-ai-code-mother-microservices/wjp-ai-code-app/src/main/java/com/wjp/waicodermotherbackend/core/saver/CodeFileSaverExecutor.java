package com.wjp.waicodermotherbackend.core.saver;

import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码文件保存执行器
 * 根据代码生成类型执行和保存逻辑
 */
public class CodeFileSaverExecutor {
    /**
     * HTML代码保存器模版
     */
    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaver = new HtmlCodeFileSaverTemplate();
    /**
     * 多文件代码保存器模版
     */
    private static final MultiFileCodeFileSaverTemplate multiFileCodeFileSaver = new MultiFileCodeFileSaverTemplate();

    /**
     * 保存代码
     * @param result 解析结果
     * @param codeGenType 代码生成类型
     * @return 保存的目录
     */
    public static File saveCode(Object result, CodeGenTypeEnum codeGenType, Long appId, int version) {
        return switch (codeGenType) {
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) result, appId, version);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) result, appId, version);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };
    }
}
