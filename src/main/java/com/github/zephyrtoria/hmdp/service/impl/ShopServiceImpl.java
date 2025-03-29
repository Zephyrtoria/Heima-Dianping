package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Shop;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IShopService;
import com.github.zephyrtoria.hmdp.mapper.ShopMapper;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    @Override
    public Result queryById(Long id) {
        // 1. 从 Redis 中查询商铺缓存
        // 因为商铺信息为静态，所以直接使用String存即可
        String shopKey = SHOP_CACHE_REDIS_PREFIX + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey + id);

        // 2. 判断缓存是否命中
        if (StrUtil.isBlank(shopJson)) {
            // 3. 缓存命中，返回商铺信息
            // 转回成对象
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 4. 缓存未命中，查询数据库
        Shop shop = getById(id);
        // 5. 数据库中商铺不存在，返回错误
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 6. 数据库中商铺存在，写入 Redis 并返回
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}




