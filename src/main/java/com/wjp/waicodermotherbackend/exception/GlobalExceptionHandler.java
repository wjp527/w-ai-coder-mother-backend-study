package com.wjp.waicodermotherbackend.exception;

import com.wjp.waicodermotherbackend.common.BaseResponse;
import com.wjp.waicodermotherbackend.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

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
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
