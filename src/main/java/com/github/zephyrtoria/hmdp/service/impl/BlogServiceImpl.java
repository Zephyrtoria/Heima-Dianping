package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.consts.SystemConstants;
import com.github.zephyrtoria.hmdp.entity.Blog;
import com.github.zephyrtoria.hmdp.entity.User;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IBlogService;
import com.github.zephyrtoria.hmdp.mapper.BlogMapper;
import com.github.zephyrtoria.hmdp.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::queryBlogUser);
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

        return Result.ok(blog);
    }
}




