package com.wjp.waicodermotherbackend.ai.tools;

import cn.hutool.json.JSONObject;
import com.wjp.waicodermotherbackend.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool{

    @Tool("修改文件内容，用新内容替换指定的旧内容")
    public String modifyFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要替换的旧内容")
            String oldContent,
            @P("要替换的新内容")
            String newContent,
            @ToolMemoryId
            Long appId
    ) {
        try {
            // 将 字符串 转换为 Path对象
            Path path = Paths.get(relativeFilePath);
            if(!path.isAbsolute()) {
                // 项目文件名称
                String projectDirName = "vue_project_" + appId;
                // 项目根目录
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                // 项目中某个文件在磁盘中的完整路径
                path = projectRoot.resolve(relativeFilePath);
            }
            if(!Files.exists(path) || !Files.isRegularFile(path)) {
                return "错误：文件不存在或不是文件 - " + relativeFilePath;
            }
            // 读取文件内容
            String originalContent = Files.readString(path);
            if(!originalContent.contains(oldContent)) {
                return "警告：文件中未找到要替换的内容，文件未修改 - " + relativeFilePath;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if(originalContent.equals(modifiedContent)) {
                return "警告：文件内容未修改 - " + relativeFilePath;
            }
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件：{}", path.toAbsolutePath());
            return "成功修改文件：" + relativeFilePath;
        } catch (Exception e) {
            String errorMsg = "修改文件失败：" + relativeFilePath + "，错误：" + e.getMessage();
            log.error(errorMsg, e);
            return errorMsg;
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        // 获取参数
        // 这写参数都是源于 在 modifyFile 方法中传递的参数
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");

        // 显示对比内容
        return String.format("""
                [工具调用] %s %s
                
                替换前
                ```
                %s
                ```
                
                替换后
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
    }
}
