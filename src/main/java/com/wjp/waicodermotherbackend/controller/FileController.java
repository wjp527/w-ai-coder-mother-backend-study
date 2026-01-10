package com.wjp.waicodermotherbackend.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.common.BaseResponse;
import com.wjp.waicodermotherbackend.common.ResultUtils;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.model.vo.FileUploadVO;
import com.wjp.waicodermotherbackend.service.FileUploadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 文件上传控制器
 *
 * @author wjp
 */
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private FileUploadService fileUploadService;

    /**
     * 临时文件目录
     */
    private static final String TEMP_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator +  "file_upload";

    /**
     * 文件上传接口（自动识别文件类型）
     * 根据文件后缀名自动选择合适的上传策略
     *
     * @param file 上传的文件
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    public BaseResponse<FileUploadVO> upload(@RequestParam("file") MultipartFile file) {
        // 1. 参数校验
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        String originalFilename = file.getOriginalFilename();
        ThrowUtils.throwIf(StrUtil.isBlank(originalFilename), ErrorCode.PARAMS_ERROR, "文件名不能为空");

        File tempFile = null;
        try {
            // 2. 创建临时文件
            tempFile = createTempFile(file, originalFilename);

            // 3. 自动识别文件类型并上传（根据文件后缀名自动选择策略）
            String url = fileUploadService.upload(tempFile, originalFilename);

            // 4. 验证上传结果
            ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.SYSTEM_ERROR, "文件上传失败");

            // 5. 返回结果
            FileUploadVO fileUploadVO = new FileUploadVO(url, originalFilename);
            log.info("文件上传成功: {} -> {}", originalFilename, url);
            return ResultUtils.success(fileUploadVO);

        } catch (RuntimeException e) {
            // 运行时异常直接抛出
            log.error("文件上传异常: {}", originalFilename, e);
            throw e;
        } catch (Exception e) {
            // 其他异常包装为运行时异常
            log.error("文件上传异常: {}", originalFilename, e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        } finally {
            // 6. 清理临时文件
            cleanupTempFile(tempFile);
        }
    }

    /**
     * 创建临时文件
     *
     * @param multipartFile 上传的文件
     * @param originalFilename 原始文件名
     * @return 临时文件
     * @throws IOException IO异常
     */
    private File createTempFile(MultipartFile multipartFile, String originalFilename) throws IOException {
        // 确保临时目录存在
        File tempDir = new File(TEMP_DIR);
        if (!tempDir.exists()) {
            FileUtil.mkdir(tempDir);
        }

        // 生成临时文件名：uuid_原始文件名
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String tempFileName = uuid + "_" + originalFilename;
        File tempFile = new File(tempDir, tempFileName);

        // 将 MultipartFile 转换为 File
        multipartFile.transferTo(tempFile);
        log.debug("创建临时文件: {}", tempFile.getAbsolutePath());

        return tempFile;
    }

    /**
     * 清理临时文件
     *
     * @param tempFile 临时文件
     */
    private void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                boolean deleted = tempFile.delete();
                if (deleted) {
                    log.debug("临时文件清理成功: {}", tempFile.getAbsolutePath());
                } else {
                    log.warn("临时文件清理失败: {}", tempFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("清理临时文件时发生异常: {}", tempFile.getAbsolutePath(), e);
            }
        }
    }
}
