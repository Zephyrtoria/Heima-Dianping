package com.github.zephyrtoria.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.zephyrtoria.hmdp.entity.Shop;
import com.github.zephyrtoria.hmdp.entity.dto.RedisDTO;
import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.mapper.ShopMapper;
import com.github.zephyrtoria.hmdp.service.IShopService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.github.zephyrtoria.hmdp.consts.ShopConstants.*;

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

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存穿透实现
        // Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿实现
        // Shop shop = queryWithMutex(id);

        Shop shop = queryWithLogicExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    // 用逻辑过期解决缓存击穿
    public Shop queryWithLogicExpire(Long id) {
        // 1. 从 Redis 中查询商铺缓存
        // 因为商铺信息为静态，所以直接使用String存即可
        String shopKey = SHOP_CACHE_REDIS_PREFIX + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2. 判断缓存是否命中
        // 因为是逻辑过期，热点查询数据都事先在Redis中预热好了
        if (StrUtil.isBlank(shopJson)) {
            // 3. 缓存未命中，返回空
            return null;
        }
        // 4. 缓存命中，判断是否过期
        // 4.1 JSON反序列化为对象
        // 即使使用了泛型也要这样写，涉及到了泛型在编译时的行为：会被擦除
        RedisDTO redisDTO = JSONUtil.toBean(shopJson, RedisDTO.class);
        JSONObject data = (JSONObject) redisDTO.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisDTO.getExpireTime();

        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回店铺信息
            return shop;
        }
        // 5.2 已过期，需要缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = SHOP_LOCK_REDIS_PREFIX + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断获取锁是否成功
        if (isLock) {
            // 6.3 成功，开启独立线程，实现缓存重建
            // 注意，获取锁成功之后应该double check缓存
            shopJson = stringRedisTemplate.opsForValue().get(shopKey);
            expireTime = JSONUtil.toBean(shopJson, RedisDTO.class).getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                return shop;
            }
            // 线程应当使用线程池，不要自行创建线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShopToRedis(id, SHOP_LOGIC_EXPIRE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4 返回过期的商铺信息
        return shop;
    }

    public void saveShopToRedis(Long id, Long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        // 2. 封装逻辑过期时间
        RedisDTO data = new RedisDTO();
        data.setData(shop);
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(SHOP_CACHE_REDIS_PREFIX + id, JSONUtil.toJsonStr(data));
    }

    // 互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id) {
        // 1. 从 Redis 中查询商铺缓存
        // 因为商铺信息为静态，所以直接使用String存即可
        String shopKey = SHOP_CACHE_REDIS_PREFIX + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2. 判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 缓存命中，返回商铺信息
            // 转回成对象
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 不为null但无值，即""空字符串
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }

        // 4. 实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = SHOP_LOCK_REDIS_PREFIX + id;
        Shop shop = null;
        try {
            boolean isLock;
            while (true) {
                isLock = tryLock(lockKey);
                // 4.2 判断是否成功
                if (isLock) {
                    break;
                }
                // 4.3 失败，休眠并重试
                Thread.sleep(SHOP_LOCK_SLEEP);
            }
            // 4.4 成功，double check 缓存中是否存在数据
            shopJson = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // 模拟重建延迟
            Thread.sleep(200);

            // 如果double check 缓存中还是不存在数据，那么再查询数据库
            shop = getById(id);

            // 5. 数据库中商铺不存在
            if (shop == null) {
                // 写入空值
                stringRedisTemplate.opsForValue().set(shopKey, "", SHOP_CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误
                return null;
            }
            // 6. 数据库中商铺存在，写入 Redis 并返回
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), SHOP_CACHE_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7. 释放互斥锁
            unlock(lockKey);
        }

        // 8. 返回
        return shop;
    }

    // 缓存穿透
    public Shop queryWithPassThrough(Long id) {
        // 1. 从 Redis 中查询商铺缓存
        // 因为商铺信息为静态，所以直接使用String存即可
        String shopKey = SHOP_CACHE_REDIS_PREFIX + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2. 判断缓存是否命中
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 缓存命中，返回商铺信息
            // 转回成对象
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 不为null但无值，即""空字符串
        if (shopJson != null) {
            // 返回错误信息
            return null;
        }
        // 4. 缓存未命中，查询数据库
        Shop shop = getById(id);
        // 5. 数据库中商铺不存在
        if (shop == null) {
            // 写入空值
            stringRedisTemplate.opsForValue().set(shopKey, "", SHOP_CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误
            return null;
        }
        // 6. 数据库中商铺存在，写入 Redis 并返回
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), SHOP_CACHE_TTL, TimeUnit.MINUTES);
        return shop;
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

    private boolean tryLock(String key) {
        // setnx
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", SHOP_LOCK_TTL, TimeUnit.SECONDS);
        // 直接返回可能会出现空指针
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}




