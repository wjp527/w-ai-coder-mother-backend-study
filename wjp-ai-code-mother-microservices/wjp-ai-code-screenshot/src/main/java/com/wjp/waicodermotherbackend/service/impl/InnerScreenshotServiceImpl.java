package com.wjp.waicodermotherbackend.service.impl;

import com.wjp.waicodermotherbackend.innerservice.InnerScreenshotService;
import com.wjp.waicodermotherbackend.service.ScreenshotService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 内部调用的截图服务实现类
 */
@DubboService
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    @Resource
    private ScreenshotService screenshotService;
    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        return screenshotService.generateAndUploadScreenshot(webUrl);
    }
}
