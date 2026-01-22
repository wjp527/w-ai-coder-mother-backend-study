package com.wjp.waicodeuser.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.wjp.waicodermotherbackend.model.dto.user.UserQueryRequest;
import com.wjp.waicodermotherbackend.model.entity.User;
import com.wjp.waicodermotherbackend.model.vo.LoginUserVO;
import com.wjp.waicodermotherbackend.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userAccount 用户账户
     * @param userPassword 用户密码
     * @param checkPassword 密码校验
     * @return
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     * @param userAccount 用户账户
     * @param userPassword 用户密码
     * @param request    请求
     * @return 登录用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户信息
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request 请求
     * @return 是否注销成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏后的用户信息
     * @param user  用户
     * @return 脱敏后的用户信息
     */
    LoginUserVO getLoginUserVO(User user);


    /**
     * 用户脱敏的单个用户信息
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 用户脱敏的用户列表信息
     * @param userList
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 查询请求转换为QueryWrapper对象
     * @param userQueryRequest 查询请求
     * @return
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    String getENCRYPT_PASSWORD(String userPassword);
}
