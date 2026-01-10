package com.wjp.waicodermotherbackend.strategy;

import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.strategy.impl.DocxUploadStrategy;
import com.wjp.waicodermotherbackend.strategy.impl.ImageUploadStrategy;
import com.wjp.waicodermotherbackend.strategy.impl.MarkdownUploadStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件上传策略工厂
 *
 * @author wjp
 */
@Component
@Slf4j
public class FileUploadStrategyFactory {

    @Resource
    private List<FileUploadStrategy> strategies;

    /**
     * 根据文件名获取对应的上传策略
     *
     * @param filename 文件名
     * @return 对应的上传策略
     * @throws BusinessException 如果找不到支持该文件类型的策略
     */
    public FileUploadStrategy getStrategy(String filename) {
        if (filename == null || filename.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }

        // 遍历所有策略，查找支持该文件类型的策略
        for (FileUploadStrategy strategy : strategies) {
            if (strategy.supports(filename)) {
                log.debug("为文件 {} 选择策略: {}", filename, strategy.getClass().getSimpleName());
                return strategy;
            }
        }

        // 没有找到支持的策略
        throw new BusinessException(ErrorCode.PARAMS_ERROR,
                String.format("不支持的文件类型: %s，支持的格式：.docx, .jpg/.jpeg/.png/.gif/.webp, .md/.markdown", filename));
    }

    /**
     * 获取DOCX上传策略
     *
     * @return DOCX上传策略
     */
    public FileUploadStrategy getDocxStrategy() {
        return strategies.stream()
                .filter(s -> s instanceof DocxUploadStrategy)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "DOCX上传策略未找到"));
    }

    /**
     * 获取图片上传策略
     *
     * @return 图片上传策略
     */
    public FileUploadStrategy getImageStrategy() {
        return strategies.stream()
                .filter(s -> s instanceof ImageUploadStrategy)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "图片上传策略未找到"));
    }

    /**
     * 获取Markdown上传策略
     *
     * @return Markdown上传策略
     */
    public FileUploadStrategy getMarkdownStrategy() {
        return strategies.stream()
                .filter(s -> s instanceof MarkdownUploadStrategy)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "Markdown上传策略未找到"));
    }
}
