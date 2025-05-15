package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.consts.SystemConstants;
import com.github.zephyrtoria.hmdp.entity.Blog;
import com.github.zephyrtoria.hmdp.entity.Follow;
import com.github.zephyrtoria.hmdp.entity.User;
import com.github.zephyrtoria.hmdp.entity.dto.UserDTO;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.entity.result.ScrollResult;
import com.github.zephyrtoria.hmdp.service.IBlogService;
import com.github.zephyrtoria.hmdp.mapper.BlogMapper;
import com.github.zephyrtoria.hmdp.service.IFollowService;
import com.github.zephyrtoria.hmdp.service.IUserService;
import com.github.zephyrtoria.hmdp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.zephyrtoria.hmdp.consts.BlogConstants.BLOG_LIKED_PREFIX;
import static com.github.zephyrtoria.hmdp.consts.RedisConstants.FEED_PREFIX;

/**
 * @author 23240
 * @description 针对表【tb_blog】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
        implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isLiked(blog);
        });
        return Result.ok(records);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result getBlogById(Long id) {
        // 1. 查询笔记
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        // 2. 查询相关用户
        queryBlogUser(blog);

        // 3. 设置liked标记
        isLiked(blog);


        return Result.ok(blog);
    }

    private void isLiked(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();

        // 2. 判断当前用户是否已经点赞
        String key = BLOG_LIKED_PREFIX + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlogById(Long id) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();

        // 2. 判断当前用户是否已经点赞
        String key = BLOG_LIKED_PREFIX + id;
        // Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 3. 未点赞
            // 3.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 3.2 保存用户到Redis的set集合
                // 为实现排行榜功能，使用sorted set实现，使用当前时间作为score
                // stringRedisTemplate.opsForSet().add(key, userId.toString());
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4. 已点赞
            // 4.1 数据库点赞-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2 从Redis的set集合中删除用户
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result getLikes(Long id) {
        String key = BLOG_LIKED_PREFIX + id;
        // 1. 查询top5的点赞用户 zrange key 0 4，注意查询获取的是score，而需要的是用户，还需要进行一个解析
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2. 解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        String idStr = StrUtil.join(",", ids);
        // 3. 根据用户id获取用户 要确保获取顺序先后满足要求 -> WHERE id IN (x, y) ORDER BY FIELD (id, x, y)
        List<UserDTO> userDTOs = userService.query().in("id", ids).
                last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        // 4. 返回
        return Result.ok(userDTOs);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2. 保存探店博文。要求保存后推送给所有粉丝
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        // 3. 查询作者的所有粉丝 -> follow表 select user_id from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        // 4. 推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1 获取粉丝id
            Long userId = follow.getUserId();
            // 4.2 推送，使用ZSet
            String key = FEED_PREFIX + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = FEED_PREFIX + userId;

        // 2. 查询收件箱 ZSet ZREVRANGEBYSCORE key max min LIMIT offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // TypedTuple = (value, score)
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 3. 解析数据：blogId + score（时间戳）+ offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 3.1 获取id
            ids.add(Long.valueOf(tuple.getValue()));
            // 3.2 获取score
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                os = 1;
                minTime = time;
            }
        }

        // 4. 根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        // 设置blog的状态
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isLiked(blog);
        }

        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setOffset(os);
        result.setMinTime(minTime);

        // 5. 封装并返回
        return Result.ok(result);
    }
}