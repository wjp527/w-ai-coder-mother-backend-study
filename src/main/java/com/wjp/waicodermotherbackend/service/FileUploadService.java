package com.wjp.waicodermotherbackend.service;

import java.io.File;

/**
 * 文件上传服务
 *
 * @author wjp
 */
public interface FileUploadService {

    /**
     * 上传DOCX文档
     *
     * @param file     文件对象
     * @param filename 文件名
     * @return 文件的访问URL，失败返回null
     */
    String uploadDocument(File file, String filename);

    /**
     * 上传图片
     *
     * @param file     文件对象
     * @param filename 文件名
     * @return 文件的访问URL，失败返回null
     */
    String uploadImage(File file, String filename);

    /**
     * 上传Markdown文档
     *
     * @param file     文件对象
     * @param filename 文件名
     * @return 文件的访问URL，失败返回null
     */
    String uploadMarkdown(File file, String filename);

    /**
     * 自动识别文件类型并上传
     *
     * @param file     文件对象
     * @param filename 文件名
     * @return 文件的访问URL，失败返回null
     */
    String upload(File file, String filename);
}
