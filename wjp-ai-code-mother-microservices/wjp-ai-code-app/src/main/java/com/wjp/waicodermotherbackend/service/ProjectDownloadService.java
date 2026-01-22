package com.wjp.waicodermotherbackend.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目下载服务实现类
 */
public interface ProjectDownloadService {
    /**
     * 项目打包下载
     * @param projectPath 项目路径
     * @param downloadFileName 下载文件名
     * @param response 响应
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
