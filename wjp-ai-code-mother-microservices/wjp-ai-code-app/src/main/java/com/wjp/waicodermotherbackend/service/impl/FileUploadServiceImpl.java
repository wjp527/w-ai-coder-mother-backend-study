package com.wjp.waicodermotherbackend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.manager.CosManager;
import com.wjp.waicodermotherbackend.service.FileUploadService;
import com.wjp.waicodermotherbackend.strategy.FileUploadStrategy;
import com.wjp.waicodermotherbackend.strategy.FileUploadStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 文件上传服务实现
 *
 * @author wjp
 */
@Service
@Slf4j
public class FileUploadServiceImpl implements FileUploadService {

    @Resource
    private CosManager cosManager;

    @Resource
    private FileUploadStrategyFactory strategyFactory;

    @Override
    public String uploadDocument(File file, String filename) {
        log.info("开始上传DOCX文档: {}", filename);
        FileUploadStrategy strategy = strategyFactory.getDocxStrategy();
        return uploadFile(file, filename, strategy);
    }

    @Override
    public String uploadImage(File file, String filename) {
        log.info("开始上传图片: {}", filename);
        FileUploadStrategy strategy = strategyFactory.getImageStrategy();
        return uploadFile(file, filename, strategy);
    }

    @Override
    public String uploadMarkdown(File file, String filename) {
        log.info("开始上传Markdown文档: {}", filename);
        FileUploadStrategy strategy = strategyFactory.getMarkdownStrategy();
        return uploadFile(file, filename, strategy);
    }

    @Override
    public String upload(File file, String filename) {
        log.info("开始自动识别文件类型并上传: {}", filename);
        // 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(filename), ErrorCode.PARAMS_ERROR, "文件名不能为空");
        ThrowUtils.throwIf(file == null, ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 根据文件名自动选择策略
        FileUploadStrategy strategy = strategyFactory.getStrategy(filename);
        return uploadFile(file, filename, strategy);
    }

    /**
     * 通用的文件上传方法
     *
     * @param file     文件对象
     * @param filename 文件名
     * @param strategy 上传策略
     * @return 文件的访问URL，失败返回null
     */
    private String uploadFile(File file, String filename, FileUploadStrategy strategy) {
        try {
            // 1. 验证文件
            strategy.validate(file, filename);

            // 2. 生成COS对象键
            String cosKey = strategy.generateKey(filename);

            // 3. 上传到COS
            String url = cosManager.uploadFile(cosKey, file);
            if (StrUtil.isNotBlank(url)) {
                log.info("文件上传成功: {} -> {}", filename, url);
                return url;
            } else {
                log.error("文件上传失败，返回URL为空: {}", filename);
                return null;
            }
        } catch (Exception e) {
            log.error("文件上传异常: {}", filename, e);
            throw e;
        }
    }
}
