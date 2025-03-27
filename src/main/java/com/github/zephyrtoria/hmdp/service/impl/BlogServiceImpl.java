package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Blog;
import com.github.zephyrtoria.hmdp.service.IBlogService;
import com.github.zephyrtoria.hmdp.mapper.BlogMapper;
import org.springframework.stereotype.Service;

/**
* @author 23240
* @description 针对表【tb_blog】的数据库操作Service实现
* @createDate 2025-03-27 13:33:15
*/
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements IBlogService {

}




