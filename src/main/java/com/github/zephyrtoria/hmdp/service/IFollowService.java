package com.github.zephyrtoria.hmdp.service;

import com.github.zephyrtoria.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.zephyrtoria.hmdp.entity.result.Result;

/**
* @author 23240
* @description 针对表【tb_follow】的数据库操作Service
* @createDate 2025-03-27 13:33:15
*/
public interface IFollowService extends IService<Follow> {

    Result setFollower(Long followUserId, Boolean isFollow);

    Result isFollowing(Long followUserId);

    Result followCommons(Long id);
}
