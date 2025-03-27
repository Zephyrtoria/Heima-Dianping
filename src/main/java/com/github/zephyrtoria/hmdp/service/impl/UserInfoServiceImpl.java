package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.UserInfo;
import com.github.zephyrtoria.hmdp.service.IUserInfoService;
import com.github.zephyrtoria.hmdp.mapper.UserInfoMapper;
import org.springframework.stereotype.Service;

/**
* @author 23240
* @description 针对表【tb_user_info】的数据库操作Service实现
* @createDate 2025-03-27 13:33:15
*/
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo>
    implements IUserInfoService {

}




