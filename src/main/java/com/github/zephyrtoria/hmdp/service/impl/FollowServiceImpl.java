package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Follow;
import com.github.zephyrtoria.hmdp.entity.User;
import com.github.zephyrtoria.hmdp.entity.dto.UserDTO;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IFollowService;
import com.github.zephyrtoria.hmdp.mapper.FollowMapper;
import com.github.zephyrtoria.hmdp.service.IUserService;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 23240
 * @description 针对表【tb_follow】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow>
        implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result setFollower(Long followUserId, Boolean isFollow) {
        // 0. 获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;

        // 1. 判断是 关注 还是 取关 操作
        if (isFollow) {
            // 2. 关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 3. 取关，删除数据
            // 要注意 userId 和 followUserId
            remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }

        return Result.ok();
    }

    @Override
    public Result isFollowing(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        String key2 = "follows:" + id;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }
        List<Long> collect = intersect.stream().map(Long::valueOf).toList();
        List<UserDTO> users = userService.listByIds(collect).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).toList();

        return Result.ok(users);
    }
}




