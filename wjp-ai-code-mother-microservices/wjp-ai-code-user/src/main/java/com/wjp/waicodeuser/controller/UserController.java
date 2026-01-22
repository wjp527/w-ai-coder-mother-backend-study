package com.wjp.waicodeuser.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import com.wjp.waicodermotherbackend.annotation.AuthCheck;
import com.wjp.waicodermotherbackend.common.BaseResponse;
import com.wjp.waicodermotherbackend.common.DeleteRequest;
import com.wjp.waicodermotherbackend.common.ResultUtils;
import com.wjp.waicodermotherbackend.constant.UserConstant;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.exception.ThrowUtils;
import com.wjp.waicodermotherbackend.model.dto.user.*;
import com.wjp.waicodermotherbackend.model.vo.LoginUserVO;
import com.wjp.waicodermotherbackend.model.vo.UserVO;
import com.wjp.waicodeuser.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.wjp.waicodermotherbackend.model.entity.User;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 用户 控制层。
 *
 * @author <a href="https://github.com/wjp527">π</a>
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册。
     * @param userRegisterRequest  用户
     * @return 0 表示注册失败，其他值表示注册成功
     */
    @PostMapping("register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();

        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录。
     * @param userLoginRequest 用户登录请求
     * @param request 请求
     * @return 登录用户信息
     */
    @PostMapping("login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户信息
     * @param request
     * @return
     */
    // 权限注解
    // @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @GetMapping("/get/loginUser")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     * @param request 请求
     * @return 是否注销成功
     */
    @PostMapping("logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }

    /**
     * 创建用户(仅管理员)
     */
    @PostMapping("add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> add(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码: 12345678
        final String DEFAULT_PASSWORD = "12345678";
        // 加密
        String encryptPassword = userService.getENCRYPT_PASSWORD(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean save = userService.save(user);
        ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据id获取用户(仅管理员)
     */
    @GetMapping("get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(@PathVariable Long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据id获取包装类
     */
    @GetMapping("get/vo")
    public BaseResponse<UserVO> getVO(Long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        BaseResponse<User> response = getUserById(id);
        if(response == null) {
            throw new RuntimeException("用户不存在");
        }
        User user = response.getData();
        UserVO userVO = userService.getUserVO(user);
        return ResultUtils.success(userVO);
    }

    /**
     * 删除用户
     */
    @PostMapping("delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> delete(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        boolean res = userService.removeById(id);
        return ResultUtils.success(res);
    }

    /**
     * 更新用户
     */
    @PostMapping("update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> update(@RequestBody UserUpdateRequest userUpdateRequest) {
        ThrowUtils.throwIf(userUpdateRequest == null || userUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return  ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表(管理员)
     */
    @PostMapping("list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);

        int pageNum = userQueryRequest.getPageNum();
        int pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest));

        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);

        return ResultUtils.success(userVOPage);
    }

    /**
     * 保存用户。
     *
     * @param user 用户
     * @return {@code true} 保存成功，{@code false} 保存失败
     */
    @PostMapping("save")
    public boolean save(@RequestBody User user) {
        return userService.save(user);
    }

    /**
     * 根据主键删除用户。
     *
     * @param id 主键
     * @return {@code true} 删除成功，{@code false} 删除失败
     */
    @DeleteMapping("remove/{id}")
    public boolean remove(@PathVariable Long id) {
        return userService.removeById(id);
    }

    /**
     * 根据主键更新用户。
     *
     * @param user 用户
     * @return {@code true} 更新成功，{@code false} 更新失败
     */
    @PutMapping("update1")
    public boolean update1(@RequestBody User user) {
        return userService.updateById(user);
    }

    /**
     * 查询所有用户。
     *
     * @return 所有数据
     */
    @GetMapping("list")
    public List<User> list() {
        return userService.list();
    }

    /**
     * 根据主键获取用户。
     *
     * @param id 用户主键
     * @return 用户详情
     */
    @GetMapping("getInfo/{id}")
    public User getInfo(@PathVariable Long id) {
        return userService.getById(id);
    }

    /**
     * 分页查询用户。
     *
     * @param page 分页对象
     * @return 分页对象
     */
    @GetMapping("page")
    public Page<User> page(Page<User> page) {
        return userService.page(page);
    }

}
