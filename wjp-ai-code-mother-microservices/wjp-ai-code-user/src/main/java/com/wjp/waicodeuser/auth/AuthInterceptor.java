package com.wjp.waicodeuser.auth;

import com.wjp.waicodermotherbackend.annotation.AuthCheck;
import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.entity.User;
import com.wjp.waicodermotherbackend.model.enums.UserRoleEnum;
import com.wjp.waicodeuser.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 拦截器
     * @param joinPoint 切入点
     * @param authCheck 注解（拥有什么权限）
     * @return 拦截结果
     * @throws Throwable 抛出异常
     */
    @Around("@annotation(authCheck)") // 生效范围(controller)
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 现在注解需要的权限
        String mustRole = authCheck.mustRole();

        // 根据当前的请求获取当前登录用户
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 当前登录用户信息
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);

        // 不选权限，放行
        if(mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 以下为: 必须有该权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 没有权限 拒绝
        if(userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 必须要求有管理员权限，但是该用户的身份不是管理员，拒绝
        if(UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 通过权限校验，放行(只适用于2中身份，如果再多一种就会出错，因为在上面也说了，如果上面的if通过，那么就证明他是普通用户，如果有个ban身份的用户，算不算就会问题了)
        return joinPoint.proceed();



    }

}
