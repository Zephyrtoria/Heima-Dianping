package com.github.zephyrtoria.hmdp.service;

import com.github.zephyrtoria.hmdp.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.zephyrtoria.hmdp.entity.dto.LoginFormDTO;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import jakarta.servlet.http.HttpSession;

/**
* @author 23240
* @description 针对表【tb_user】的数据库操作Service
* @createDate 2025-03-27 13:33:15
*/
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();
}
