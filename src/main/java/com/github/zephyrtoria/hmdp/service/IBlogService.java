package com.github.zephyrtoria.hmdp.service;

import com.github.zephyrtoria.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.zephyrtoria.hmdp.entity.result.Result;

/**
* @author 23240
* @description 针对表【tb_blog】的数据库操作Service
* @createDate 2025-03-27 13:33:15
*/
public interface IBlogService extends IService<Blog> {
    Result queryHotBlog(Integer current);

    Result getBlogById(Long id);

    Result likeBlogById(Long id);

    Result getLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
