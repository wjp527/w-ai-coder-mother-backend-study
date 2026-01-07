package com.wjp.waicodermotherbackend.ai.tools;

import cn.hutool.json.JSONObject;
import com.wjp.waicodermotherbackend.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件删除工具
 * 支持 AI 通过工具调用的方式删除文件
 */
@Slf4j
@Component
public class FileDeleteTool extends BaseTool{

    @Tool("删除指定路径的文件")
    public String deleteFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @ToolMemoryId
            Long appId
    ) {
        try {
            // 将 字符串 转换为 Path对象
            Path path = Paths.get(relativeFilePath);
            // 判断是否为 绝对路径
            // 如果不是 绝对路径，则创建基于 appId 的项目目录
            if(!path.isAbsolute()) {
                // 项目目录名称
                String projectDirName = "vue_project_" + appId;
                // 项目根目录
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                // 把项目根目录 与 文件的相对路径拼接 = 一个文件他在磁盘上的完整路径
                path = projectRoot.resolve(relativeFilePath);
            }
            if(!Files.exists(path)) {
                return "警告：文件不存在，无需删除 - " + relativeFilePath;
            }
            if(!Files.isRegularFile(path)) {
                return "错误：指定路径不是文件，无法删除 - " + relativeFilePath;
            }
            // 安全检查：避免删除重要文件
            String fileName = path.getFileName().toString();
            if(isImportantFile(fileName)) {
                return "错误：文件是重要文件，不允许删除 - " + relativeFilePath;
            }
            Files.delete(path);
            log.info("成功删除文件：{}", path.toAbsolutePath());
            return "成功删除文件：" + relativeFilePath;
        } catch (Exception e) {
            String errorMsg = "删除文件失败：" + relativeFilePath + "，错误：" + e.getMessage();
            log.error(errorMsg, e);
            return errorMsg;
        }
    }


    /**
     * 判断是否是重要文件，不允许删除
     */
    private boolean isImportantFile(String fileName) {
        String[] importantFiles = {
                "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
                "vite.config.js", "vite.config.ts", "vue.config.js",
                "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json",
                "index.html", "main.js", "main.ts", "App.vue", ".gitignore", "README.md"
        };
        for (String important : importantFiles) {
            if (important.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getToolName() {
        return "deleteFile";
    }

    @Override
    public String getDisplayName() {
        return "删除文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 获取参数
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[工具调用] %s %s",getDisplayName(), relativeFilePath);
    }
}
