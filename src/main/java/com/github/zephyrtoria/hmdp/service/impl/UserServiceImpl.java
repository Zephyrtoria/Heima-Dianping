package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.User;
import com.github.zephyrtoria.hmdp.entity.dto.LoginFormDTO;
import com.github.zephyrtoria.hmdp.entity.dto.UserDTO;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IUserService;
import com.github.zephyrtoria.hmdp.mapper.UserMapper;
import com.github.zephyrtoria.hmdp.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号错误");
        }

        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 保存验证码和手机号
        session.setAttribute(LOGIN_CODE_KEY, code);
        session.setAttribute(LOGIN_PHONE_KEY, phone);

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
        if (!session.getAttribute(LOGIN_PHONE_KEY).equals(phone)) {
            return Result.fail("手机号不对应");
        }

        // 2. 校验验证码
        String cacheCode = (String) session.getAttribute(LOGIN_CODE_KEY);
        String code = loginForm.getCode();
        if (!cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3. 根据手机号查询用户
        User user = query().eq(LOGIN_PHONE_KEY, phone).one();
        if (user == null) {
            // 4.1 用户不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        // 4.2 用户存在
        session.setAttribute(LOGIN_USER_KEY, BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}




