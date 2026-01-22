package com.wjp.waicodermotherbackend.innerservice;

import com.wjp.waicodermotherbackend.exception.BusinessException;
import com.wjp.waicodermotherbackend.exception.ErrorCode;
import com.wjp.waicodermotherbackend.model.entity.User;
import com.wjp.waicodermotherbackend.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static com.wjp.waicodermotherbackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 内部用户服务
 */
public interface InnerUserService {

    List<User> listByIds(Collection<? extends Serializable> ids);

    User getById(Serializable id);

    UserVO getUserVO(User user);

    /**
     * 静态方法：避免跨服务调用
     * 不能使用默认方法否则 Dubbo 会尝试序列化，导致报错
     */
    static User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if(currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
}
