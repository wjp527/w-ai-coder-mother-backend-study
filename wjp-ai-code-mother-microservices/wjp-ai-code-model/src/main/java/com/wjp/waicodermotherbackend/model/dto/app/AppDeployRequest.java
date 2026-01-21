package com.wjp.waicodermotherbackend.model.dto.app;

import lombok.Data;

import java.io.Serializable;

/**
 * 部署请求类
 */
@Data
public class AppDeployRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 应用版本
     */
    private Long version;

    private static final long serialVersionUID = 1L;
}
