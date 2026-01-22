package com.wjp.waicodermotherbackend.strategy;

import java.io.File;

/**
 * 文件上传策略接口
 *
 * @author wjp
 */
public interface FileUploadStrategy {

    /**
     * 验证文件类型是否支持
     *
     * @param filename 文件名
     * @return 是否支持该文件类型
     */
    boolean supports(String filename);

    /**
     * 验证文件
     *
     * @param file     文件对象
     * @param filename 文件名
     */
    void validate(File file, String filename);

    /**
     * 生成COS对象键（路径）
     *
     * @param filename 原始文件名
     * @return COS对象键
     */
    String generateKey(String filename);

    /**
     * 获取基础目录
     *
     * @return 基础目录名称
     */
    String getBaseDir();
}
