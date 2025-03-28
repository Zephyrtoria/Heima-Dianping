package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Shop;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IShopService;
import com.github.zephyrtoria.hmdp.mapper.ShopMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
* @author 23240
* @description 针对表【tb_shop】的数据库操作Service实现
* @createDate 2025-03-27 13:33:15
*/
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop>
    implements IShopService {
}




