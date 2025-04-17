package com.github.zephyrtoria.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.zephyrtoria.hmdp.entity.dto.RedisDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_LOCK_REDIS_PREFIX;
import static com.github.zephyrtoria.hmdp.consts.ShopConstants.SHOP_LOCK_TTL;

@Slf4j
@Component
public class RedisClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisDTO redisDTO = new RedisDTO();
        redisDTO.setData(value);
        redisDTO.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisDTO), time, unit);
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long ttl, TimeUnit unit
    ) {
        // 1. 从 Redis 中查询
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否命中
        if (StrUtil.isNotBlank(json)) {
            // 3. 缓存命中，返回信息
            return JSONUtil.toBean(json, type);
        }
        // 不为null但无值，即""空字符串
        if (json != null) {
            // 返回错误信息
            return null;
        }
        // 4. 缓存未命中，查询数据库
        // 函数式编程，传入一个函数变量
        R r = dbFallback.apply(id);
        // 5. 数据库中不存在
        if (r == null) {
            // 写入空值
            stringRedisTemplate.opsForValue().set(key, "", ttl, unit);
            // 返回错误
            return null;
        }
        // 6. 数据库中存在，写入 Redis 并返回
        this.set(key, r, ttl, unit);
        return r;
    }

    public <R, ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long ttl, TimeUnit unit) {
        // 1. 从 Redis 中查询商铺缓存
        // 因为商铺信息为静态，所以直接使用String存即可
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否命中
        // 如果是逻辑过期，则热点查询数据都事先在Redis中预热好了，一定会查得到
        if (StrUtil.isBlank(json)) {
            // 3. 缓存未命中，返回空
            return null;
        }

        // 4. 缓存命中，判断是否过期
        // 4.1 JSON反序列化为对象
        // 即使使用了泛型也要这样写，涉及到了泛型在编译时的行为：会被擦除
        RedisDTO redisDTO = JSONUtil.toBean(json, RedisDTO.class);
        JSONObject data = (JSONObject) redisDTO.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisDTO.getExpireTime();

        // 5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期，直接返回店铺信息
            return r;
        }
        // 6. 已过期，需要缓存重建
        // 6.1 获取互斥锁
        // 锁的前缀最好是另外定义
        String lockKey = SHOP_LOCK_REDIS_PREFIX + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断获取锁是否成功
        if (isLock) {
            // 6.3 成功，开启独立线程，实现缓存重建
            // 注意，获取锁成功之后应该double check缓存
            /*json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(json)) {
                expireTime = JSONUtil.toBean(json, RedisDTO.class).getExpireTime();
                if (expireTime.isAfter(LocalDateTime.now())) {
                    return r;
                }
            }*/
            // 开启独立线程
            // 线程应当使用线程池，不要自行创建线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    R apply = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, apply, ttl, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4 返回过期的商铺信息
        return r;
    }

    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = SHOP_LOCK_REDIS_PREFIX + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4.获取锁成功，根据id查询数据库
            r = dbFallback.apply(id);
            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", time, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放锁
            unlock(lockKey);
        }
        // 8.返回
        return r;
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
