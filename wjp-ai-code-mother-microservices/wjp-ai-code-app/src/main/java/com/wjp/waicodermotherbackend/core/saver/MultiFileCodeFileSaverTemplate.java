package com.wjp.waicodermotherbackend.core.saver;

import com.wjp.waicodermotherbackend.ai.model.MultiFileCodeResult;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;

/**
 * 多文件代码保存器模版
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {
    /**
     * 保存文件
     * @param result 解析结果
     * @param baseDirPath 基础目录路径
     */
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    /**
     * 获取代码类型
     * @return 代码类型枚举
     */
    @Override
    protected CodeGenTypeEnum getCodeGenType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    /**
     * 验证输入
     * @param result 多文件代码结果
     */
    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        if (result.getHtmlCode() == null || result.getHtmlCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码不能为空");
        }
    }
}
