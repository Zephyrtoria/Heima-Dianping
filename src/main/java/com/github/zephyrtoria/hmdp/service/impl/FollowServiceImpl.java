package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Follow;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IFollowService;
import com.github.zephyrtoria.hmdp.mapper.FollowMapper;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * @author 23240
 * @description 针对表【tb_follow】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow>
        implements IFollowService {

    @Override
    public Result setFollower(Long followUserId, Boolean isFollow) {
        // 0. 获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 1. 判断是 关注 还是 取关 操作
        if (isFollow) {
            // 2. 关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
        } else {
            // 3. 取关，删除数据
            // 要注意 userId 和 followUserId
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
        }

        return Result.ok();
    }

    @Override
    public Result isFollowing(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }
}




