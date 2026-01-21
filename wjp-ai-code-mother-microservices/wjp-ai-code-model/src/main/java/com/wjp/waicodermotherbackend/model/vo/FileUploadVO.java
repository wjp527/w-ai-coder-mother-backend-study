package com.wjp.waicodermotherbackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传响应VO
 *
 * @author wjp
 */
@Data
public class FileUploadVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件访问URL
     */
    private String url;

    /**
     * 文件名
     */
    private String filename;

    public FileUploadVO() {
    }

    public FileUploadVO(String url, String filename) {
        this.url = url;
        this.filename = filename;
    }
}
