package com.wjp.waicodermotherbackend.exception;

import cn.hutool.json.JSONUtil;
import com.wjp.waicodermotherbackend.common.BaseResponse;
import com.wjp.waicodermotherbackend.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.Map;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    /**
     * 处理文件上传大小超限异常
     *
     * @param e 异常
     * @return 友好错误提示
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<?> maxUploadSizeExceededExceptionHandler(MaxUploadSizeExceededException e) {
        log.warn("文件上传大小超限: {}", e.getMessage());

        // 提取文件大小限制信息，提供友好提示
        String message = "文件大小超出限制，单个文件最大允许上传 50 MB";

        try {
            // 尝试从异常中获取最大文件大小限制
            if (e.getMaxUploadSize() > 0) {
                double maxSizeMB = e.getMaxUploadSize() / (1024.0 * 1024.0);
                if (maxSizeMB < 1) {
                    // 如果小于1MB，显示KB
                    double maxSizeKB = e.getMaxUploadSize() / 1024.0;
                    message = String.format("文件大小超出限制，单个文件最大允许上传 %.2f KB", maxSizeKB);
                } else {
                    message = String.format("文件大小超出限制，单个文件最大允许上传 %.2f MB", maxSizeMB);
                }
            } else if (e.getMessage() != null) {
                // 从异常消息中提取限制大小（备用方案）
                String msg = e.getMessage();
                if (msg.contains("bytes")) {
                    String sizeStr = msg.replaceAll("[^0-9]", "");
                    if (!sizeStr.isEmpty()) {
                        try {
                            long maxSizeBytes = Long.parseLong(sizeStr);
                            double maxSizeMB = maxSizeBytes / (1024.0 * 1024.0);
                            if (maxSizeMB < 1) {
                                double maxSizeKB = maxSizeBytes / 1024.0;
                                message = String.format("文件大小超出限制，单个文件最大允许上传 %.2f KB", maxSizeKB);
                            } else {
                                message = String.format("文件大小超出限制，单个文件最大允许上传 %.2f MB", maxSizeMB);
                            }
                        } catch (NumberFormatException ignored) {
                            // 解析失败，使用默认消息
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("解析文件大小限制信息失败", ex);
            // 使用默认提示
        }

        return ResultUtils.error(ErrorCode.FILE_SIZE_EXCEEDED, message);
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        
        // 检查是否是 SSE 请求
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), getErrorMessage(e))) {
            return null;
        }
        
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, getErrorMessage(e));
    }
    
    /**
     * 从异常中提取友好的错误信息
     */
    private String getErrorMessage(RuntimeException e) {
        String message = e.getMessage();
        
        if (message == null) {
            return "系统错误";
        }
        
        // 处理 DeepSeek API 余额不足错误
        if (message.contains("Insufficient Balance")) {
            return "AI 服务账户余额不足，请联系管理员";
        }
        
        // 尝试从 JSON 格式的错误信息中提取 message 字段
        // 格式示例: {"error":{"message":"Insufficient Balance",...}}
        if (message.contains("\"message\"") && message.contains("{")) {
            try {
                // 查找 "message":" 的位置
                int messageKeyIndex = message.indexOf("\"message\":\"");
                if (messageKeyIndex > 0) {
                    int messageStart = messageKeyIndex + 11; // "message":" 的长度
                    int messageEnd = message.indexOf("\"", messageStart);
                    if (messageEnd > messageStart) {
                        String extractedMessage = message.substring(messageStart, messageEnd);
                        if (extractedMessage.contains("Insufficient Balance")) {
                            return "AI 服务账户余额不足，请联系管理员";
                        }
                        // 返回提取的消息，如果为空则使用默认
                        return extractedMessage.isEmpty() ? "系统错误" : extractedMessage;
                    }
                }
            } catch (Exception ignored) {
                // 解析失败，继续使用默认逻辑
            }
        }
        
        // 如果是 InvalidRequestException，尝试提取更详细的信息
        if (message.contains("InvalidRequestException")) {
            // 已经尝试过 JSON 解析，如果失败则返回通用错误
            return "AI 服务请求失败，请稍后重试";
        }
        
        // 默认错误信息
        return "系统错误";
    }


    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        // 尝试处理 SSE 请求
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        // 对于普通请求，返回标准 JSON 响应
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理SSE请求的错误响应
     *
     * @param errorCode 错误码
     * @param errorMessage 错误信息
     * @return true表示是SSE请求并已处理，false表示不是SSE请求
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return false;
        }
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        // 判断是否是SSE请求（通过Accept头或URL路径）
        String accept = request.getHeader("Accept");
        String uri = request.getRequestURI();
        if ((accept != null && accept.contains("text/event-stream")) ||
                uri.contains("/chat/gen/code")) {
            try {
                // 设置SSE响应头
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                response.setHeader("Connection", "keep-alive");
                // 构造错误消息的SSE格式
                Map<String, Object> errorData = Map.of(
                        "error", true,
                        "code", errorCode,
                        "message", errorMessage
                );
                String errorJson = JSONUtil.toJsonStr(errorData);
                // 发送业务错误事件（避免与标准error事件冲突）
                String sseData = "event: business-error\ndata: " + errorJson + "\n\n";
                response.getWriter().write(sseData);
                response.getWriter().flush();
                // 发送结束事件
                response.getWriter().write("event: done\ndata: {}\n\n");
                response.getWriter().flush();
                // 表示已处理SSE请求
                return true;
            } catch (IOException ioException) {
                log.error("Failed to write SSE error response", ioException);
                // 即使写入失败，也表示这是SSE请求
                return true;
            }
        }
        return false;
    }
}
