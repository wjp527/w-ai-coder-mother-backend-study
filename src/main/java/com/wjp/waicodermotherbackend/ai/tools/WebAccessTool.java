package com.wjp.waicodermotherbackend.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Web 访问工具
 * 支持 AI 通过工具调用的方式访问外部 URL 并获取网页内容
 *
 * @author wjp
 */
@Slf4j
@Component
public class WebAccessTool extends BaseTool {

    /**
     * 最大内容长度（字符数），避免超出 token 限制
     */
    private static final int MAX_CONTENT_LENGTH = 50000; // 约 50KB 文本

    /**
     * 默认超时时间（毫秒）
     */
    private static final int DEFAULT_TIMEOUT = 10000; // 10秒

    /**
     * HTML 标签模式，用于提取纯文本
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * DOCX 文件的 MIME 类型
     */
    private static final String DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_CONTENT_TYPE = "application/octet-stream";

    @Tool("访问指定的网页 URL 或文档 URL 并获取其文本内容。支持 HTML 网页和 DOCX 文档格式。用于获取网页信息、文档内容等。URL 必须是完整的 HTTP 或 HTTPS 地址。")
    public String accessWeb(
            @P("要访问的网页或文档 URL，必须是完整的 HTTP 或 HTTPS 地址。支持 HTML 网页和 DOCX 文档（.docx 扩展名），例如：https://www.example.com 或 https://example.com/document.docx")
            String url,
            @ToolMemoryId
            Long appId
    ) {
        try {
            // 1. 验证 URL 格式
            if (StrUtil.isBlank(url)) {
                return "错误：URL 不能为空";
            }

            // 确保 URL 包含协议
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // 验证 URL 格式
            try {
                new URL(url);
            } catch (Exception e) {
                return "错误：无效的 URL 格式 - " + url;
            }

            log.info("开始访问资源: {}", url);

            // 2. 检测文件类型（通过 URL 扩展名）
            String contentType = null;
            boolean isDocx = url.toLowerCase().endsWith(".docx") || url.toLowerCase().contains(".docx?");
            
            // 3. 发送 HTTP 请求
            HttpResponse response;
            try {
                HttpRequest request = HttpRequest.get(url)
                        .timeout(DEFAULT_TIMEOUT)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                
                // 如果是 DOCX 文件，调整 Accept header
                if (isDocx) {
                    request.header("Accept", "*/*");
                } else {
                    request.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                }
                
                response = request.execute();
            } catch (Exception e) {
                String errorMsg = "访问资源失败: " + url;
                log.error(errorMsg, e);
                if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                    return "错误：" + errorMsg + "，请求超时（超过 " + (DEFAULT_TIMEOUT / 1000) + " 秒）";
                }
                return "错误：" + errorMsg + "，原因: " + e.getMessage();
            }

            // 4. 检查响应状态
            int statusCode = response.getStatus();
            if (statusCode != 200) {
                return "错误：访问资源失败，HTTP 状态码: " + statusCode + "，URL: " + url;
            }

            // 5. 获取 Content-Type
            contentType = response.header("Content-Type");
            if (contentType != null) {
                contentType = contentType.toLowerCase();
                isDocx = isDocx || contentType.contains("wordprocessingml") || 
                         contentType.contains("application/vnd.openxmlformats");
            }

            // 6. 根据文件类型处理内容
            String textContent;
            if (isDocx) {
                log.info("检测到 DOCX 文档，开始解析: {}", url);
                textContent = extractTextFromDocx(response);
            } else {
                // 7. 获取 HTML 响应内容
                String htmlContent = response.body();
                if (StrUtil.isBlank(htmlContent)) {
                    return "警告：网页内容为空，URL: " + url;
                }

                // 8. 提取文本内容（去除 HTML 标签）
                textContent = extractTextFromHtml(htmlContent);
            }

            // 9. 限制内容长度
            if (textContent != null && textContent.length() > MAX_CONTENT_LENGTH) {
                textContent = textContent.substring(0, MAX_CONTENT_LENGTH);
                textContent += "\n\n[内容已截断，原始内容超过 " + MAX_CONTENT_LENGTH + " 字符]";
            }

            if (StrUtil.isBlank(textContent)) {
                return "警告：未能提取到文本内容，URL: " + url;
            }

            log.info("成功访问资源，内容长度: {} 字符，类型: {}，URL: {}", 
                    textContent.length(), isDocx ? "DOCX" : "HTML", url);
            return textContent;

        } catch (Exception e) {
            String errorMsg = "访问网页时发生异常: " + url;
            log.error(errorMsg, e);
            return "错误：" + errorMsg + "，详情: " + e.getMessage();
        }
    }

    /**
     * 从 HTML 内容中提取纯文本
     *
     * @param html HTML 内容
     * @return 纯文本内容
     */
    private String extractTextFromHtml(String html) {
        if (StrUtil.isBlank(html)) {
            return "";
        }

        // 移除 script 和 style 标签及其内容（使用非贪婪匹配，处理多行）
        html = html.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?is)<style[^>]*>.*?</style>", "");

        // 移除 HTML 注释
        html = html.replaceAll("(?s)<!--.*?-->", "");

        // 移除 HTML 标签
        String text = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");

        // 解码常见的 HTML 实体
        text = decodeHtmlEntities(text);

        // 合并多个空白字符为单个空格
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");

        // 移除首尾空白并返回
        return text.trim();
    }

    /**
     * HTML 实体解码模式
     */
    private static final Pattern HTML_ENTITY_DECIMAL_PATTERN = Pattern.compile("&#(\\d+);");
    private static final Pattern HTML_ENTITY_HEX_PATTERN = Pattern.compile("&#x([0-9a-fA-F]+);", Pattern.CASE_INSENSITIVE);

    /**
     * 解码 HTML 实体
     *
     * @param text 包含 HTML 实体的文本
     * @return 解码后的文本
     */
    private String decodeHtmlEntities(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }

        // 处理十进制数字实体（&#123;）
        Matcher decimalMatcher = HTML_ENTITY_DECIMAL_PATTERN.matcher(text);
        StrBuilder builder = new StrBuilder();
        int lastEnd = 0;
        while (decimalMatcher.find()) {
            builder.append(text, lastEnd, decimalMatcher.start());
            try {
                int codePoint = Integer.parseInt(decimalMatcher.group(1));
                if (codePoint > 0 && codePoint <= 0x10FFFF) {
                    builder.append((char) codePoint);
                } else {
                    builder.append(decimalMatcher.group());
                }
            } catch (Exception e) {
                builder.append(decimalMatcher.group());
            }
            lastEnd = decimalMatcher.end();
        }
        builder.append(text, lastEnd, text.length());
        text = builder.toString();

        // 处理十六进制数字实体（&#x1F;）
        Matcher hexMatcher = HTML_ENTITY_HEX_PATTERN.matcher(text);
        builder = new StrBuilder();
        lastEnd = 0;
        while (hexMatcher.find()) {
            builder.append(text, lastEnd, hexMatcher.start());
            try {
                int codePoint = Integer.parseInt(hexMatcher.group(1), 16);
                if (codePoint > 0 && codePoint <= 0x10FFFF) {
                    builder.append((char) codePoint);
                } else {
                    builder.append(hexMatcher.group());
                }
            } catch (Exception e) {
                builder.append(hexMatcher.group());
            }
            lastEnd = hexMatcher.end();
        }
        builder.append(text, lastEnd, text.length());
        text = builder.toString();

        // 处理常见的命名实体（先处理 &amp; 避免重复替换）
        text = text.replace("&amp;", "\u0001AMP\u0001")  // 临时占位符
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&copy;", "©")
                .replace("&reg;", "®")
                .replace("&trade;", "™")
                .replace("\u0001AMP\u0001", "&");  // 恢复 & 符号

        return text;
    }

    @Override
    public String getToolName() {
        return "accessWeb";
    }

    @Override
    public String getDisplayName() {
        return "访问网页";
    }

    /**
     * 从 DOCX 文档中提取文本内容
     *
     * @param response HTTP 响应对象
     * @return 提取的文本内容
     */
    private String extractTextFromDocx(HttpResponse response) {
        File tempFile = null;
        File tempDir = null;
        try {
            // 1. 创建临时目录和文件
            String tempDirPath = System.getProperty("java.io.tmpdir") + File.separator + "docx_parse_" + System.currentTimeMillis();
            tempDir = new File(tempDirPath);
            FileUtil.mkdir(tempDir);

            tempFile = new File(tempDir, "document.docx");
            
            // 2. 下载文件到临时位置
            try (InputStream inputStream = response.bodyStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                long maxSize = 10 * 1024 * 1024; // 限制最大 10MB
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > maxSize) {
                        return "错误：DOCX 文件过大，超过 10MB 限制";
                    }
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            log.debug("DOCX 文件已下载到临时位置: {}", tempFile.getAbsolutePath());

            // 3. 解压 ZIP 并提取 word/document.xml
            String xmlContent = null;
            try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(tempFile.toPath()))) {
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (entry.getName().equals("word/document.xml")) {
                        // 读取 XML 内容（使用缓冲区方式）
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }
                        xmlContent = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
                        break;
                    }
                    zipInputStream.closeEntry();
                }
            }

            if (StrUtil.isBlank(xmlContent)) {
                return "错误：无法从 DOCX 文件中找到 word/document.xml";
            }

            // 4. 解析 XML 提取文本
            String textContent = parseDocxXml(xmlContent);
            
            if (StrUtil.isBlank(textContent)) {
                return "警告：DOCX 文档中没有可提取的文本内容";
            }

            return textContent;

        } catch (Exception e) {
            String errorMsg = "解析 DOCX 文档失败";
            log.error(errorMsg, e);
            return "错误：" + errorMsg + "，详情: " + e.getMessage();
        } finally {
            // 5. 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                FileUtil.del(tempFile);
            }
            if (tempDir != null && tempDir.exists()) {
                FileUtil.del(tempDir);
            }
        }
    }

    /**
     * 解析 DOCX 的 XML 内容，提取文本
     *
     * @param xmlContent XML 内容
     * @return 提取的文本
     */
    private String parseDocxXml(String xmlContent) {
        try {
            // 使用正则表达式提取 <w:t> 标签中的文本
            // DOCX 的 XML 中，文本内容通常在 <w:t> 标签中
            // 模式支持带命名空间或不带命名空间的标签：<w:t> 或 <t>
            Pattern textPattern = Pattern.compile("<(?:w:)?t[^>]*>(.*?)</(?:w:)?t>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = textPattern.matcher(xmlContent);
            
            StrBuilder textBuilder = new StrBuilder();
            while (matcher.find()) {
                String text = matcher.group(1);
                // 解码 XML 实体
                text = text.replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'");
                
                if (StrUtil.isNotBlank(text)) {
                    textBuilder.append(text).append(" ");
                }
            }

            String result = textBuilder.toString().trim();
            
            // 如果正则方法失败或结果为空，尝试使用 XML 解析器（备用方案）
            if (StrUtil.isBlank(result)) {
                log.debug("正则表达式未提取到文本，尝试使用 XML 解析器");
                result = parseDocxXmlWithParser(xmlContent);
            }

            // 合并多个空白字符
            if (StrUtil.isNotBlank(result)) {
                result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ");
            }

            return result;

        } catch (Exception e) {
            log.warn("使用正则表达式解析 DOCX XML 失败，尝试使用 XML 解析器", e);
            return parseDocxXmlWithParser(xmlContent);
        }
    }

    /**
     * 使用 XML 解析器解析 DOCX XML（备用方案）
     *
     * @param xmlContent XML 内容
     * @return 提取的文本
     */
    private String parseDocxXmlWithParser(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            StrBuilder textBuilder = new StrBuilder();
            extractTextFromNode(doc.getDocumentElement(), textBuilder);

            return textBuilder.toString().trim();
        } catch (Exception e) {
            log.error("使用 XML 解析器解析 DOCX 失败", e);
            return "";
        }
    }

    /**
     * 递归提取 XML 节点中的文本内容
     *
     * @param node XML 节点
     * @param builder 文本构建器
     */
    private void extractTextFromNode(Node node, StrBuilder builder) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            String text = node.getTextContent();
            if (StrUtil.isNotBlank(text)) {
                builder.append(text);
            }
        } else if (node.getNodeType() == Node.ELEMENT_NODE) {
            // DOCX 中文本在 w:t 元素中
            if (node.getNodeName().endsWith(":t") || node.getNodeName().equals("t")) {
                String text = node.getTextContent();
                if (StrUtil.isNotBlank(text)) {
                    builder.append(text);
                }
            } else {
                NodeList children = node.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    extractTextFromNode(children.item(i), builder);
                }
            }
        } else {
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                extractTextFromNode(children.item(i), builder);
            }
        }
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String url = arguments.getStr("url");
        return String.format("[工具调用] %s: %s", getDisplayName(), url);
    }
}
