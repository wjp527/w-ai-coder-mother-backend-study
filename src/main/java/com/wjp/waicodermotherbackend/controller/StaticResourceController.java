package com.wjp.waicodermotherbackend.controller;

import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.model.enums.AppCodeGenEnum;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;

import static com.wjp.waicodermotherbackend.constant.AppConstant.CODE_DEPLOY_ROOT_DIR;
import static com.wjp.waicodermotherbackend.constant.AppConstant.CODE_OUTPUT_ROOT_DIR;
import static com.wjp.waicodermotherbackend.exception.ErrorCode.SYSTEM_ERROR;

/**
 * 静态资源访问控制器
 *
 * 该控制器负责处理静态资源的访问请求，包括：
 * 1. 预览应用的静态资源访问
 * 2. 已部署应用的静态资源访问
 * 3. 支持目录重定向和默认文件访问
 *
 * 访问格式：
 * - 预览：/api/static/preview/{fileName}/**
 * - 部署：/api/static/deploy/{deployKey}/**
 *
 * @author dhbxs
 * @since 2025/8/4
 * @version 1.0
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    /**
     * 访问预览应用的静态资源
     *
     * 该方法处理预览应用的静态资源请求，支持通配符路径访问。
     * 当用户访问预览应用时，会从临时生成目录中读取文件。
     *
     * @param fileName 预览应用的文件名标识
     * @param request HTTP请求对象，用于获取完整的请求路径
     * @return 包含静态资源的响应实体
     *
     * @example 访问示例：
     * GET /api/static/preview/multi_file_123/index.html
     * 将返回 tmp/code_output/multi_file_123/index.html 文件
     */
    @GetMapping("/preview/{fileName}/**")
    public ResponseEntity<Resource> serveStaticPreviewResource(@PathVariable String fileName, HttpServletRequest request) {
        return getResource(AppCodeGenEnum.CODE_OUTPUT_ROOT_DIR, fileName, request);
    }

    /**
     * 访问已部署应用的静态资源
     *
     * 该方法处理已部署应用的静态资源请求，支持通配符路径访问。
     * 当用户访问已部署的应用时，会从部署目录中读取文件。
     *
     * @param deployKey 部署应用的唯一标识键
     * @param request HTTP请求对象，用于获取完整的请求路径
     * @return 包含静态资源的响应实体
     *
     * @example 访问示例：
     * GET /api/static/deploy/deploy_key_123/index.html
     * 将返回 tmp/code_deploy/deploy_key_123/index.html 文件
     */
    @GetMapping("/deploy/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticDeployResource(@PathVariable String deployKey, HttpServletRequest request) {
        return getResource(AppCodeGenEnum.CODE_DEPLOY_ROOT_DIR, deployKey, request);
    }

    /**
     * 核心静态资源处理方法
     *
     * 该方法负责处理所有静态资源访问的核心逻辑，包括：
     * 1. 路径解析和构建
     * 2. 文件存在性检查
     * 3. 目录重定向处理
     * 4. 资源类型识别和响应头设置
     * 5. 错误处理和日志记录
     *
     * @param appCodeGenEnum 应用代码生成枚举，用于确定资源根目录
     * @param fileNameOrDeployKey 文件名或部署键，用于构建具体的文件路径
     * @param request HTTP请求对象，包含完整的请求信息
     * @return 包含静态资源的响应实体，或错误响应
     *
     * @throws BusinessException 当访问类型未知时抛出业务异常
     */
    private ResponseEntity<Resource> getResource(AppCodeGenEnum appCodeGenEnum, String fileNameOrDeployKey, HttpServletRequest request) {
        // 从请求属性中获取完整的资源路径
        // 这个路径包含了从 /static 开始到请求结束的完整路径
        String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        // 构建文件路径变量
        String filePath = null;

        // 根据应用类型处理不同的路径前缀
        if (appCodeGenEnum.equals(AppCodeGenEnum.CODE_OUTPUT_ROOT_DIR)) {
            // 预览模式：移除 /static/preview/{fileName} 前缀
            resourcePath = resourcePath.substring(("/static/preview/" + fileNameOrDeployKey).length());
        } else if (appCodeGenEnum.equals(AppCodeGenEnum.CODE_DEPLOY_ROOT_DIR)) {
            // 部署模式：移除 /static/deploy/{deployKey} 前缀
            resourcePath = resourcePath.substring(("/static/deploy/" + fileNameOrDeployKey).length());
        } else {
            // 未知的访问类型，抛出业务异常
            throw new BusinessException(SYSTEM_ERROR, "未知访问类型");
        }

        try {
            // 处理目录访问的情况
            if (resourcePath.isEmpty()) {
                // 如果访问的是目录根路径（如 /static/preview/fileName），重定向到带斜杠的URL
                // 这样可以确保后续的路径处理逻辑能正确识别为目录访问
                HttpHeaders headers = new HttpHeaders();
                headers.add(HttpHeaders.LOCATION, request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }

            // 处理根目录访问的情况
            if (resourcePath.equals("/")) {
                // 当访问根目录时，默认返回 index.html 文件
                // 这是Web应用的标准做法
                resourcePath = "/index.html";
            }

            // 构建完整的文件系统路径
            // 格式：{根目录}/{应用标识}/{相对路径}
            filePath = appCodeGenEnum.getValue() + File.separator + fileNameOrDeployKey + resourcePath;

            // 创建文件对象并检查存在性
            File file = new File(filePath);

            // 检查文件是否存在
            if (!file.exists()) {
                // 文件不存在，返回404错误
                return ResponseEntity.notFound().build();
            }

            // 创建Spring的资源对象
            // FileSystemResource是Spring提供的文件系统资源实现
            Resource resource = new FileSystemResource(file);

            // 根据文件扩展名设置正确的Content-Type和字符编码
            String contentType = getContentTypeWithCharset(filePath);

            // 返回成功的响应，包含文件内容和正确的Content-Type
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(resource);

        } catch (Exception e) {
            // 捕获所有异常，记录错误信息并返回500错误
            // 这样可以避免异常信息泄露给客户端，同时便于调试
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的Content-Type
     *
     * 该方法根据文件扩展名智能识别文件类型，并为文本文件添加UTF-8字符编码。
     * 这样可以确保浏览器能正确解析文件内容，特别是包含中文等特殊字符的文件。
     *
     * @param filePath 文件路径，用于提取文件扩展名
     * @return 包含字符编码的Content-Type字符串
     *
     * @example 返回值示例：
     * - .html -> "text/html; charset=UTF-8"
     * - .css -> "text/css; charset=UTF-8"
     * - .js -> "application/javascript; charset=UTF-8"
     * - .png -> "image/png"
     * - .jpg -> "image/jpeg"
     * - 其他 -> "application/octet-stream"
     */
    private String getContentTypeWithCharset(String filePath) {
        // HTML文件：设置HTML类型和UTF-8编码
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";

        // CSS文件：设置CSS类型和UTF-8编码
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";

        // JavaScript文件：设置JavaScript类型和UTF-8编码
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";

        // 图片文件：使用Spring的MediaType常量
        if (filePath.endsWith(".png")) return MediaType.IMAGE_PNG.toString();
        if (filePath.endsWith(".jpg")) return MediaType.IMAGE_JPEG.toString();

        // 其他文件类型：使用通用的二进制流类型
        return "application/octet-stream";
    }
}
