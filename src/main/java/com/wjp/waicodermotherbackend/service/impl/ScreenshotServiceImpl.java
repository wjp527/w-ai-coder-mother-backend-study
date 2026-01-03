package com.wjp.waicodermotherbackend.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.manager.CosManager;
import com.wjp.waicodermotherbackend.service.ScreenshotService;
import com.wjp.waicodermotherbackend.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 截图服务
 */
@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private CosManager cosManager;


    /**
     * 生成并上传截图
     * @param webUrl 网页地址
     * @return 截图的访问URL
     */
    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "网页地址不能为空");
        log.info("开始生成网页截图, URL: {}", webUrl);

        // 1、生成本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.SYSTEM_ERROR, "截图生成失败");

        try {
            // 2、上传到 COS
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.SYSTEM_ERROR, "上传截图失败");
            log.info("截图上传成功, URL: {}", cosUrl);
            return cosUrl;

        } finally {
            // 3、清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    private String uploadScreenshotToCos(String localScreenshotPath) {
        if(StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if(!screenshotFile.exists()) {
            log.error("截图文件不存在：{}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的 COS 对象键
     * @param fileName 文件名
     * @return /screenshots/2026/1/3/fileName.jpg
     */
    private String generateScreenshotKey(String fileName) {
        String dataPath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", dataPath, fileName);
    }

    /**
     * 清理本地文件
     * @param localScreenshotPath 本地文件路径
     */
    private void cleanupLocalFile(String localScreenshotPath) {
        File localFile = new File(localScreenshotPath);
        if(localFile.exists()) {
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("清理本地文件成功：{}", localScreenshotPath);
        }
    }

}
