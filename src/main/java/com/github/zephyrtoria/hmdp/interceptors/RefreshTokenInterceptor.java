package com.github.zephyrtoria.hmdp.interceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.github.zephyrtoria.hmdp.entity.dto.UserDTO;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.zephyrtoria.hmdp.consts.LoginConstants.LOGIN_USER_REDIS_PREFIX;
import static com.github.zephyrtoria.hmdp.consts.LoginConstants.LOGIN_USER_REDIS_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            // 不存在token
            // 注意这里之所以放行，是因为这里仅是第一层拦截器，之后会由LoginInterceptor再进行拦截
            return true;
        }
        // 2. 基于token获取redis中的用户
        String tokenKey = LOGIN_USER_REDIS_PREFIX + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        // 3. 判断用户是否存在
        if (userMap.isEmpty()) {
            // 4.1 不存在则拦截
            return true;
        }
        // 4.2 存在，将Hash数据转为UserDTO对象，并保存用户信息到 ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 5. 刷新token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_REDIS_TTL, TimeUnit.MINUTES);
        // 6. 放行
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
