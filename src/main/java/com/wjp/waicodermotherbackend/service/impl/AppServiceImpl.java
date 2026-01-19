package com.wjp.waicodermotherbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjp.waicodermotherbackend.ai.AiCodeGenTypeRoutingService;
import com.wjp.waicodermotherbackend.ai.AiCodeGenTypeRoutingServiceFactory;
import com.wjp.waicodermotherbackend.constant.AppConstant;
import com.wjp.waicodermotherbackend.constant.UserConstant;
import com.wjp.waicodermotherbackend.core.AiCodeGeneratorFacade;
import com.wjp.waicodermotherbackend.core.builder.VueProjectBuilder;
import com.wjp.waicodermotherbackend.core.handler.StreamHandlerExecutor;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.model.dto.app.AppAddRequest;
import com.wjp.waicodermotherbackend.model.dto.app.AppQueryRequest;
import com.wjp.waicodermotherbackend.model.dto.app.AppVO;
import com.wjp.waicodermotherbackend.model.entity.App;
import com.wjp.waicodermotherbackend.mapper.AppMapper;
import com.wjp.waicodermotherbackend.model.entity.User;
import com.wjp.waicodermotherbackend.model.enums.ChatHistoryMessageTypeEnum;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
import com.wjp.waicodermotherbackend.model.vo.UserVO;
import com.wjp.waicodermotherbackend.monitor.MonitorContext;
import com.wjp.waicodermotherbackend.monitor.MonitorContextHolder;
import com.wjp.waicodermotherbackend.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.lang.management.MonitorInfo;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * AI 服务
     */
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    /**
     * 流式处理器执行器
     */
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    @Resource
    private ChatHistoryOriginalService chatHistoryOriginalService;


    /**
     * 通过聊天生成应用代码
     *
     * @param appId     应用Id
     * @param message   prompt消息
     * @param loginUser 登录用户
     * @return 生成的代码【流式】
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1、参数校验
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用Id不能为空");
        }
        if (StrUtil.isBlank(message)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "prompt消息不能为空");
        }
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }


        // 2、查询应用信息
        App app = this.getById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        Integer version = app.getVersion();
        if (version == null || version == 0) {
            version = 1;
        }

        // 3、权限校验，仅本人可以和自己的应用对话
        Long appUserId = app.getId();
        if (!appId.equals(appUserId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        // 4、获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "代码生成类型错误");

        // 5. 将用户的消息保存到对话记忆里
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        chatHistoryOriginalService.addOriginalChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6、设置监控上下文（用户Id 和 应用Id）
        MonitorContext monitorContext = MonitorContext.builder()
                .userId(String.valueOf(loginUser.getId()))
                .appId(String.valueOf(app.getId()))
                .build();
        // 设置监控上下文
        MonitorContextHolder.setContext(monitorContext);
        // 7、调用 AI服务生成代码
        // 这里不使用 app 里面的提示词，是因为这个方法不仅仅用于创建应用，后面还需要修改，多轮对话，反不能一直用最一开始的提示词吧
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId, version);

        // 8、收集AI响应内容并在完成后记录到对话历史
        StringBuilder aiResponseBuilder = new StringBuilder();
        // 9、收集AI 响应内容并在完成后记录到对话历史中
        return streamHandlerExecutor.doExecute(contentFlux, chatHistoryService,chatHistoryOriginalService, appId, loginUser, codeGenTypeEnum)
                .doFinally(singalType -> {
                    // 流结束时清理（无论成功/失败/取消）
                    MonitorContextHolder.clearContext();
                });
    }

    /**
     * 部署应用
     *
     * @param appId     应用Id
     * @param version   应用版本
     * @param loginUser 登录用户
     * @return 部署结果
     */
    @Override
    public String deployApp(Long appId, Long version, User loginUser) {
        // 1、参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用Id不能为空");
        ThrowUtils.throwIf(version == null || version <= 0, ErrorCode.PARAMS_ERROR, "应用版本不能为空");
        // 2、应用，版本是否存在
        App app = this.getById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        Integer currentVersion = app.getVersion();
        if (version > currentVersion) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用版本不存在");
        }
        // 3、应用创建人才可以部署
        Long userId = app.getUserId();
        Long id = loginUser.getId();
        if (!id.equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无操作权限");
        }
        // 4、检查是否有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成(6位deployKey - 大小写字母 + 数字)
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5、获取代码生成类型，部署原目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        // ${应用生成目录}/${应用类型_appId}
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;

        // 6、检查原目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成");
        }
        // 7、Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "构建失败");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "Vue项目构建完成但为生成dist目录");
            // 将dist目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录：{}", distDir.getAbsolutePath());
        }

        // 8、检查源目录【应用生成路径】是否存在
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成");
        }
        // 9、复制文件到部署目录
        // ${应用部署目录}/${部署Key}/${版本}
        String deployDirName = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey + File.separator + "V" + version;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirName), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败: " + e.getMessage());
        }
        // 10、更新应用的 deployKey 和 部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.SYSTEM_ERROR, "更新应用失败");
        // 11、返回可访问的URL
        String appDeployUrl = AppConstant.CODE_DEPLOY_HOST + File.separator + deployKey + File.separator + "V" + version;
        // 12、异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;

    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId        应用Id
     * @param appDeployUrl 应用部署URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appDeployUrl) {
        // 使用虚拟现成异步执行
        Thread.startVirtualThread(() -> {
            // 1、调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appDeployUrl);
            // 2、更新数据表
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean result = this.updateById(updateApp);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新应用封面失败");
        });

    }


    /**
     * 创建应用
     * @param appAddRequest 应用创建请求
     * @param loginUser 登录用户
     * @return 应用Id
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用AI智能选择代码生成类型(多例模式)
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum codeGenTypeEnum = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(codeGenTypeEnum.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return app.getId();
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
            chatHistoryOriginalService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            log.error("删除应用关联对话历史失败: {}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }


    /**
     * 获取 AppVO 列表
     *
     * @param appList
     * @return
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }


    /**
     * 获取查询条件
     *
     * @param appQueryRequest
     * @return
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }


    /**
     * 获取App的VO信息
     *
     * @param app
     * @return
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    /**
     * 导出应用代码为Markdown文件
     *
     * @param appId     应用ID
     * @param loginUser 登录用户
     * @return 文件路径
     */
    @Override
    public String exportAppCodeToMd(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3. 权限验证：只有应用创建者或管理员可以导出
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        boolean isCreator = app.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isAdmin && !isCreator, ErrorCode.NO_AUTH_ERROR, "无权导出该应用的代码");

        // 4. 获取应用名称，处理文件名
        String appName = app.getAppName();
        if (StrUtil.isBlank(appName)) {
            appName = "app_" + appId;
        }
        String fileName = sanitizeFileName(appName) + ".md";

        // 5. 获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "应用代码生成类型错误");

        // 6. 根据codeGenType动态查询ChatHistory：查找包含相应代码块的最新记录
        com.wjp.waicodermotherbackend.model.entity.ChatHistory chatHistory = findLatestChatHistoryWithCodeBlocks(appId, codeGenTypeEnum);
        ThrowUtils.throwIf(chatHistory == null, ErrorCode.NOT_FOUND_ERROR, "未找到包含代码块的应用AI回复记录");

        String message = chatHistory.getMessage();
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.SYSTEM_ERROR, "AI回复消息为空");

        // 7. 提取代码块
        Map<String, String> codeBlocks = extractCodeBlocks(message);
        ThrowUtils.throwIf(codeBlocks.isEmpty(), ErrorCode.SYSTEM_ERROR, "未找到代码块（html、css、javascript）");

        // 8. 构建Markdown内容
        String markdownContent = buildMarkdownContent(codeBlocks);

        // 9. 保存文件到磁盘
        String exportDir = AppConstant.CODE_EXPORT_ROOT_DIR;
        FileUtil.mkdir(exportDir);
        String filePath = exportDir + File.separator + fileName;
        FileUtil.writeString(markdownContent, filePath, StandardCharsets.UTF_8);

        log.info("导出应用代码成功: appId={}, fileName={}, filePath={}", appId, fileName, filePath);
        return filePath;
    }

    /**
     * 根据codeGenType查找包含相应代码块的最新ChatHistory记录
     *
     * @param appId           应用ID
     * @param codeGenTypeEnum 代码生成类型枚举
     * @return ChatHistory记录，如果未找到则返回null
     */
    private com.wjp.waicodermotherbackend.model.entity.ChatHistory findLatestChatHistoryWithCodeBlocks(
            Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        // 查询所有AI消息，按时间降序排序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("appId", appId)
                .eq("messageType", ChatHistoryMessageTypeEnum.AI.getValue())
                .orderBy("createTime", false);
        // 限制查询数量，避免查询过多数据（最多查询100条）
        queryWrapper.limit(100);
        List<com.wjp.waicodermotherbackend.model.entity.ChatHistory> chatHistoryList = chatHistoryService.list(queryWrapper);

        if (CollUtil.isEmpty(chatHistoryList)) {
            return null;
        }

        // 根据codeGenType类型查找包含相应代码块的记录
        for (com.wjp.waicodermotherbackend.model.entity.ChatHistory chatHistory : chatHistoryList) {
            String message = chatHistory.getMessage();
            if (StrUtil.isBlank(message)) {
                continue;
            }

            // 检查是否包含所需的代码块
            boolean hasRequiredCodeBlocks = false;
            if (codeGenTypeEnum == CodeGenTypeEnum.HTML) {
                // HTML模式：只需要包含```html```代码块
                hasRequiredCodeBlocks = message.contains("```html") || message.contains("```HTML");
            } else if (codeGenTypeEnum == CodeGenTypeEnum.MULTI_FILE) {
                // MULTI_FILE模式：需要同时包含```html```、```css```、```javascript```代码块
                boolean hasHtml = message.contains("```html") || message.contains("```HTML");
                boolean hasCss = message.contains("```css") || message.contains("```CSS");
                boolean hasJs = message.contains("```javascript") || message.contains("```JavaScript")
                        || message.contains("```js") || message.contains("```JS");
                hasRequiredCodeBlocks = hasHtml && hasCss && hasJs;
            }

            if (hasRequiredCodeBlocks) {
                return chatHistory;
            }
        }

        return null;
    }

    /**
     * 从消息中提取代码块
     *
     * @param message 消息内容
     * @return 代码块Map，key为语言类型（html/css/javascript），value为代码内容
     */
    private Map<String, String> extractCodeBlocks(String message) {
        Map<String, String> codeBlocks = new LinkedHashMap<>();
        // 正则表达式：匹配 ```html、```css、```javascript 代码块
        // 模式：```(html|css|javascript)\s*\n([\s\S]*?)```
        // 支持```后可能有空白字符，使用[\s\S]匹配包括换行在内的所有字符
        Pattern pattern = Pattern.compile("```(html|css|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message);

        while (matcher.find()) {
            String language = matcher.group(1).toLowerCase();
            String code = matcher.group(2);
            // 如果同一个语言有多个代码块，保留最后一个
            codeBlocks.put(language, code);
        }

        return codeBlocks;
    }

    /**
     * 构建Markdown内容
     *
     * @param codeBlocks 代码块Map
     * @return Markdown格式的字符串
     */
    private String buildMarkdownContent(Map<String, String> codeBlocks) {
        StringBuilder sb = new StringBuilder();

        // 按照html、css、javascript的顺序输出
        String[] languages = {"html", "css", "javascript"};
        for (String lang : languages) {
            if (codeBlocks.containsKey(lang)) {
                sb.append("## ").append(lang.toUpperCase()).append("\n\n");
                sb.append("```").append(lang).append("\n");
                sb.append(codeBlocks.get(lang));
                sb.append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    /**
     * 清理文件名，移除文件系统不支持的字符
     *
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "export";
        }
        // 移除Windows和Linux文件系统不支持的字符：/ \ : * ? " < > |
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }


}
