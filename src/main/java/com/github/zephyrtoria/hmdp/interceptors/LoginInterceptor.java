package com.github.zephyrtoria.hmdp.interceptors;


import com.github.zephyrtoria.hmdp.entity.dto.UserDTO;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.github.zephyrtoria.hmdp.consts.LoginConstants.LOGIN_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {

    // 进入controller之前
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
        HttpSession session = request.getSession();
        // 2. 获取session中的用户
        Object cacheUser = session.getAttribute(LOGIN_USER_KEY);
        // 3. 判断用户是否存在
        if (cacheUser == null) {
            // 4.1 不存在则拦截
            response.setStatus(401);
            return false;
        }
        // 4.2 存在，保存用户信息到 ThreadLocal
        UserHolder.saveUser((UserDTO) cacheUser);
        // 5. 放行
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    // 渲染完成之后
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
