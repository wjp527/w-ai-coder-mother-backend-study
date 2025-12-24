package com.wjp.waicodermotherbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorService;
import com.wjp.waicodermotherbackend.ai.AiCodeGeneratorServiceFactory;
import com.wjp.waicodermotherbackend.core.AiCodeGeneratorFacade;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.model.dto.app.AppQueryRequest;
import com.wjp.waicodermotherbackend.model.dto.app.AppVO;
import com.wjp.waicodermotherbackend.model.entity.App;
import com.wjp.waicodermotherbackend.mapper.AppMapper;
import com.wjp.waicodermotherbackend.model.entity.User;
import com.wjp.waicodermotherbackend.model.enums.CodeGenTypeEnum;
import com.wjp.waicodermotherbackend.model.vo.UserVO;
import com.wjp.waicodermotherbackend.service.AppService;
import com.wjp.waicodermotherbackend.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    @Resource
    private UserService userService;

    /**
     * AI 服务
     */
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;


    /**
     * 通过聊天生成应用代码
     * @param appId 应用Id
     * @param message prompt消息
     * @param loginUser 登录用户
     * @return 生成的代码【流式】
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1、参数校验
        if(appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用Id不能为空");
        }
        if(StrUtil.isBlank(message)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "prompt消息不能为空");
        }
        if(loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }


        // 2、查询应用信息
        App app = this.getById(appId);
        if(app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }
        Integer version = app.getVersion();
        if(version == null || version == 0) {
            version = 1;
        }

        // 3、权限校验，仅本人可以和自己的应用对话
        Long appUserId = app.getId();
        if(!appId.equals(appUserId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作");
        }

        // 4、获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "代码生成类型错误");

        // 5、调用 AI服务生成代码
        // 这里不使用 app 里面的提示词，是因为这个方法不仅仅用于创建应用，后面还需要修改，多轮对话，反不能一直用最一开始的提示词吧
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(message,codeGenTypeEnum, appId, version);
    }



    /**
     * 获取 AppVO 列表
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


}
