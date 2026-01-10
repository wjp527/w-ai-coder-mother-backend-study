package com.wjp.waicodermotherbackend.strategy.impl;

import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.strategy.FileUploadStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Markdown文件上传策略
 *
 * @author wjp
 */
@Component
@Slf4j
public class MarkdownUploadStrategy implements FileUploadStrategy {

    private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList(
            ".md", ".markdown"
    );
    private static final String BASE_DIR = "markdown";

    @Override
    public boolean supports(String filename) {
        if (StrUtil.isBlank(filename)) {
            return false;
        }
        String lowerFilename = filename.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream()
                .anyMatch(lowerFilename::endsWith);
    }

    @Override
    public void validate(File file, String filename) {
        // 验证文件是否存在
        ThrowUtils.throwIf(file == null || !file.exists(), ErrorCode.PARAMS_ERROR, "文件不存在");
        // 验证文件是否为空
        ThrowUtils.throwIf(file.length() == 0, ErrorCode.PARAMS_ERROR, "文件为空");
        // 验证文件类型
        ThrowUtils.throwIf(!supports(filename), ErrorCode.PARAMS_ERROR,
                "不支持的文件类型，仅支持 .md, .markdown 格式");
    }

    @Override
    public String generateKey(String filename) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        // 生成唯一文件名：uuid_原始文件名
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = String.format("/%s/%s/%s_%s", BASE_DIR, datePath, uuid, safeFilename);
        log.debug("生成Markdown文件COS键: {}", key);
        return key;
    }

    @Override
    public String getBaseDir() {
        return BASE_DIR;
    }
}
