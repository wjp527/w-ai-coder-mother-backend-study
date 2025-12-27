package com.wjp.waicodermotherbackend.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.constant.AppConstant;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模版方法模式
 * @param <T>
 */
public abstract class CodeFileSaverTemplate<T> {

    /**
     * 文件保存的根目录
     */
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模版方法：保存代码的标准流程
     * @param result 解析结果
     * @return 保存的目录
     */
    public final File saveCode(T result, Long appId, int version) {
        // 1、验证输入
        validateInput(result);
        // 2、构建唯一目录
        String baseDirPath = buildUniqueDir(appId, version);
        // 3、保存文件(具体实现交给子类)
        saveFiles(result, baseDirPath);
        // 4、返回文件目录对象
        return new File(baseDirPath);
    }

    /**
     * 验证输入 (可由子类覆盖)
     * @param result
     */
    protected void validateInput(T result) {
        if(result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "输入参数不能为空");
        }
    }

    /**
     * 构建文件的唯一路径: tmp/code_output/bizType_雪花ID
     * @param appId 应用ID
     * @param  version 版本号
     * @return 目录路径
     */
    protected String buildUniqueDir(Long appId, int version) {
        if(appId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "appId不能为空");
        }

        String codeType = getCodeGenType().getValue();
        // 构建目录名称
        String uniqueDirName = StrUtil.format("{}_{}", codeType, appId);
        // TODO: 再建一个版本的目录，用于存放代码
        // 构建目录路径
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        // 创建目录
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存单个文件
     * @param dirPath 文件根目录
     * @param fileName 文件名
     * @param context 文件内容
     */
    public final static void writeToFile(String dirPath,String fileName, String context) {
        if(StrUtil.isNotBlank(context)) {
            // 完整的文件路径
            String filePath = dirPath + File.separator + fileName;
            // 写入文件
            FileUtil.writeString(context, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 保存文件（由子类重写）
     * @param result 解析结果
     * @param baseDirPath 基础目录路径
     */
    protected abstract void saveFiles(T result, String baseDirPath);

    /**
     * 获取代码类型 (交给子类)
     * @return 代码类型枚举
     */
    protected abstract CodeGenTypeEnum getCodeGenType();

}
