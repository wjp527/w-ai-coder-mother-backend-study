package com.wjp.waicodermotherbackend.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wjp.waicodermotherbackend.model.dto.app.AppQueryRequest;
import com.wjp.waicodermotherbackend.model.dto.app.AppVO;
import com.wjp.waicodermotherbackend.model.entity.App;
import com.wjp.waicodermotherbackend.model.entity.User;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
public interface AppService extends IService<App> {


    /**
     * 通过聊天生成应用代码
     * @param appId 应用Id
     * @param message prompt消息
     * @param loginUser 登录用户
     * @return 生成的代码【流式】
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 部署应用
     * @param appId 应用Id
     * @param version 应用版本
     * @param loginUser 登录用户
     * @return 部署结果
     */
    String deployApp(Long appId, Long version , User loginUser);

    /**
     * 异步生成应用截图并更新封面
     * @param appId 应用Id
     * @param appDeployUrl 应用部署URL
     */
    void generateAppScreenshotAsync(Long appId, String appDeployUrl);

    /**
     * 获取 AppVO 列表
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 获取查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取App的VO信息
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 导出应用代码为Markdown文件
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @return 文件路径
     */
    String exportAppCodeToMd(Long appId, User loginUser);


}
