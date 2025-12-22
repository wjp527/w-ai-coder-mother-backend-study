package com.wjp.waicodermotherbackend.core.saver;

import com.wjp.waicodermotherbackend.ai.model.HtmlCodeResult;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;

/**
 * HTML代码保存器模版
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    /**
     * 保存文件
     * @param result 解析结果
     * @param baseDirPath 基础目录路径
     */
    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    /**
     * 获取代码类型
     * @return HTML代码类型枚举
     */
    @Override
    protected CodeGenTypeEnum getCodeGenType() {
        return CodeGenTypeEnum.HTML;
    }

    /**
     * 验证输入
     * @param result HTML代码结果
     */
    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        if(result.getHtmlCode() == null || result.getHtmlCode().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码不能为空");
        }
    }
}
