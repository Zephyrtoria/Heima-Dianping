package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.User;
import com.github.zephyrtoria.hmdp.entity.dto.LoginFormDTO;
import com.github.zephyrtoria.hmdp.entity.dto.UserDTO;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.mapper.UserMapper;
import com.github.zephyrtoria.hmdp.service.IUserService;
import com.github.zephyrtoria.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.zephyrtoria.hmdp.consts.LoginConstants.*;
import static com.github.zephyrtoria.hmdp.consts.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * @author 23240
 * @description 针对表【tb_user】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号错误");
        }

        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 保存验证码和手机号到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_REDIS_PREFIX + phone, code, LOGIN_CODE_REDIS_TTL, TimeUnit.MINUTES);

        // 4. 返回验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 验证手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        // 2. 校验验证码
        // String cacheCode = (String) session.getAttribute(LOGIN_CODE_SESSION);
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_REDIS_PREFIX + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3. 根据手机号查询用户
        User user = query().eq(LOGIN_MAPPER_PHONE, phone).one();
        if (user == null) {
            // 4.1 用户不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 4.2 用户存在，保存用户信息到redis
        // 5.1 随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 5.2 将User对象转为Hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 5.3 存储到redis
        String tokenKey = LOGIN_USER_REDIS_PREFIX + token;
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                // 将值转换为字符串
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 5.4 设置有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_REDIS_TTL, TimeUnit.MINUTES);
        // 6. 返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}




