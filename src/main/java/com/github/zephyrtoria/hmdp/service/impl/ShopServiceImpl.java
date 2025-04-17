package com.github.zephyrtoria.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Shop;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.mapper.ShopMapper;
import com.github.zephyrtoria.hmdp.service.IShopService;
import com.github.zephyrtoria.hmdp.utils.RedisClient;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_CACHE_NULL_TTL;
import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_CACHE_REDIS_PREFIX;

/**
 * @author 23240
 * @description 针对表【tb_shop】的数据库操作Service实现
 * @createDate 2025-03-27 13:33:15
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop>
        implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 由Spring IoC创建的单例模式
    @Resource
    private RedisClient redisClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透实现
        Shop shop = redisClient.queryWithPassThrough(SHOP_CACHE_REDIS_PREFIX, id, Shop.class,
                 this::getById, SHOP_CACHE_NULL_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿实现
        // Shop shop = redisClient.queryWithLogicExpire(SHOP_CACHE_REDIS_PREFIX, id, Shop.class, this::getById, SHOP_CACHE_NULL_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺ID错误");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(SHOP_CACHE_REDIS_PREFIX + id);
        return Result.ok();
    }
}




